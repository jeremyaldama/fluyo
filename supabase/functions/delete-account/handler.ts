interface QueryResult<T> {
  data: T | null;
  error: unknown | null;
}

interface StorageEntry {
  id?: string | null;
  name: string;
}

interface StorageBucket {
  list(
    prefix: string,
    options: {
      limit: number;
      offset: number;
      sortBy: { column: string; order: "asc" | "desc" };
    },
  ): PromiseLike<QueryResult<StorageEntry[]>>;
  remove(paths: string[]): PromiseLike<{ error: unknown | null }>;
}

export interface EdgeClient {
  rpc(
    functionName: string,
    parameters?: Record<string, unknown>,
  ): PromiseLike<QueryResult<unknown>>;
  auth: {
    getUser(token: string): PromiseLike<{
      data: { user: { id: string } | null };
      error: unknown | null;
    }>;
    admin: {
      deleteUser(id: string): PromiseLike<{ error: unknown | null }>;
    };
  };
  storage: {
    listBuckets(): PromiseLike<QueryResult<Array<{ name: string }>>>;
    from(name: string): StorageBucket;
  };
}

export interface DeleteAccountDependencies {
  getEnv(name: string): string | undefined;
  fetch(input: string | URL | Request, init?: RequestInit): Promise<Response>;
  createClient(url: string, key: string): EdgeClient;
  logError(message: string): void;
  createRequestId?: () => string;
}

const RECEIPTS_BUCKET = "receipts";
const STORAGE_PAGE_SIZE = 100;
const REQUEST_ID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

const corsHeaders = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers":
    "authorization, x-client-info, apikey, content-type, x-request-id",
  "access-control-allow-methods": "POST, OPTIONS",
};

function responseHeaders(requestId: string): Headers {
  return new Headers({
    ...corsHeaders,
    "x-request-id": requestId,
  });
}

function emptyResponse(status: number, requestId: string): Response {
  return new Response(null, { status, headers: responseHeaders(requestId) });
}

function jsonError(status: number, error: string, requestId: string): Response {
  const headers = responseHeaders(requestId);
  headers.set("content-type", "application/json; charset=utf-8");
  return new Response(JSON.stringify({ error }), { status, headers });
}

function requestIdFor(
  request: Request,
  createRequestId: () => string,
): string {
  const supplied = request.headers.get("x-request-id")?.trim();
  return supplied && REQUEST_ID_PATTERN.test(supplied) ? supplied : createRequestId();
}

function logFailure(
  dependencies: Pick<DeleteAccountDependencies, "logError">,
  requestId: string,
  stage: string,
) {
  dependencies.logError(JSON.stringify({
    event: "delete_account_failed",
    request_id: requestId,
    stage,
  }));
}

export function bearerToken(authorization: string | null): string | null {
  return authorization?.match(/^Bearer[\t ]+(\S+)[\t ]*$/i)?.[1] ?? null;
}

/**
 * Deletes receipt objects without recursively materializing every object path.
 *
 * Each directory is fully enumerated before its children are processed. Since files
 * are removed as their page is read, the offset advances only by directory entries,
 * which remain stable until that directory's children are visited. The tombstone set
 * by the handler prevents user writes while this traversal runs.
 */
export async function deleteReceiptObjects(admin: EdgeClient, authId: string) {
  const { data: buckets, error } = await admin.storage.listBuckets();
  if (error) throw error;
  if (!buckets?.some((bucket) => bucket.name === RECEIPTS_BUCKET)) return;

  const pendingPrefixes = [authId];

  while (pendingPrefixes.length > 0) {
    // A LIFO worklist retains only the unvisited frontier, not the already traversed tree.
    const prefix = pendingPrefixes.pop()!;
    let directoryOffset = 0;

    while (true) {
      const { data, error: listError } = await admin.storage.from(RECEIPTS_BUCKET).list(
        prefix,
        {
          limit: STORAGE_PAGE_SIZE,
          offset: directoryOffset,
          sortBy: { column: "name", order: "asc" },
        },
      );
      if (listError) throw listError;
      if (!data || data.length === 0) break;

      const filePaths: string[] = [];
      let directoryCount = 0;
      for (const item of data) {
        const path = `${prefix}/${item.name}`;
        if (item.id == null) {
          pendingPrefixes.push(path);
          directoryCount += 1;
        } else {
          filePaths.push(path);
        }
      }

      if (filePaths.length > 0) {
        const { error: removalError } = await admin.storage.from(RECEIPTS_BUCKET).remove(
          filePaths,
        );
        if (removalError) throw removalError;
      }

      // Removed files shift the listing left; only folders remain at stable offsets.
      directoryOffset += directoryCount;
    }
  }
}

export async function deleteExternalArtifacts(
  authId: string,
  requestId: string,
  dependencies: Pick<DeleteAccountDependencies, "getEnv" | "fetch">,
) {
  const cleanupUrl = dependencies.getEnv("ACCOUNT_DELETION_CLEANUP_URL");
  const cleanupSecret = dependencies.getEnv("ACCOUNT_DELETION_CLEANUP_SECRET");

  // Fail closed: WhatsApp audio is outside Supabase. The external endpoint must first
  // tombstone the identity, reject new writes and then drain its objects.
  if (!cleanupUrl || !cleanupSecret) {
    throw new Error("External account-data cleanup is not configured");
  }

  const parsedCleanupUrl = new URL(cleanupUrl);
  if (
    parsedCleanupUrl.protocol !== "https:" ||
    parsedCleanupUrl.username !== "" ||
    parsedCleanupUrl.password !== ""
  ) {
    throw new Error("External cleanup URL must be HTTPS without embedded credentials");
  }

  const response = await dependencies.fetch(parsedCleanupUrl, {
    method: "POST",
    redirect: "error",
    signal: AbortSignal.timeout(15_000),
    headers: {
      "authorization": `Bearer ${cleanupSecret}`,
      "content-type": "application/json",
      "x-request-id": requestId,
    },
    body: JSON.stringify({ auth_user_id: authId }),
  });
  if (!response.ok) {
    throw new Error(`External cleanup failed with HTTP ${response.status}`);
  }
}

export function createDeleteAccountHandler(
  dependencies: DeleteAccountDependencies,
): (request: Request) => Promise<Response> {
  return async (request) => {
    const requestId = requestIdFor(
      request,
      dependencies.createRequestId ?? (() => crypto.randomUUID()),
    );

    if (request.method === "OPTIONS") return emptyResponse(204, requestId);
    if (request.method !== "POST") {
      const response = jsonError(405, "Method not allowed", requestId);
      response.headers.set("allow", "POST, OPTIONS");
      return response;
    }

    const supabaseUrl = dependencies.getEnv("SUPABASE_URL");
    const anonKey = dependencies.getEnv("SUPABASE_ANON_KEY");
    const serviceRoleKey = dependencies.getEnv("SUPABASE_SERVICE_ROLE_KEY");
    if (!supabaseUrl || !anonKey || !serviceRoleKey) {
      logFailure(dependencies, requestId, "configuration");
      return jsonError(500, "Server configuration error", requestId);
    }

    const token = bearerToken(request.headers.get("authorization"));
    if (!token) return jsonError(401, "Unauthorized", requestId);

    // Verify the JWT with Auth; never trust a decoded client-side subject.
    const userClient = dependencies.createClient(supabaseUrl, anonKey);
    const { data: { user }, error: userError } = await userClient.auth.getUser(token);
    if (userError || !user) return jsonError(401, "Unauthorized", requestId);

    const admin = dependencies.createClient(supabaseUrl, serviceRoleKey);
    let stage = "tombstone";

    try {
      // This service-role-only RPC is idempotent. Its database tombstone activates the
      // write guards before any slower external or Storage cleanup starts.
      const { error: tombstoneError } = await admin.rpc("begin_account_deletion", {
        p_auth_id: user.id,
      });
      if (tombstoneError) throw tombstoneError;

      stage = "external_cleanup";
      await deleteExternalArtifacts(user.id, requestId, dependencies);

      stage = "storage_cleanup";
      await deleteReceiptObjects(admin, user.id);

      stage = "auth_delete";
      // public.users references auth.users ON DELETE CASCADE; owned rows then cascade from
      // public.users. Only this trusted function ever receives service-role privileges.
      const { error } = await admin.auth.admin.deleteUser(user.id);
      if (error) throw error;

      return emptyResponse(204, requestId);
    } catch (_) {
      // Provider errors may carry URLs, object paths or other personal information.
      // Log only the fixed stage and an opaque correlation identifier.
      logFailure(dependencies, requestId, stage);
      return jsonError(500, "Account deletion failed", requestId);
    }
  };
}
