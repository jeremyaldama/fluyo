import assert from "node:assert/strict";
import {
  bearerToken,
  createDeleteAccountHandler,
  type DeleteAccountDependencies,
  deleteReceiptObjects,
  type EdgeClient,
} from "./handler.ts";

const BASE_ENV: Record<string, string> = {
  SUPABASE_URL: "https://project.supabase.co",
  SUPABASE_ANON_KEY: "anon-key",
  SUPABASE_SERVICE_ROLE_KEY: "service-role-key",
  ACCOUNT_DELETION_CLEANUP_URL: "https://cleanup.example.test/delete",
  ACCOUNT_DELETION_CLEANUP_SECRET: "cleanup-secret",
};
const GENERATED_REQUEST_ID = "11111111-1111-4111-8111-111111111111";
const SUPPLIED_REQUEST_ID = "22222222-2222-4222-8222-222222222222";

interface ClientFixture {
  client: EdgeClient;
  deletedUsers: string[];
  removedBatches: string[][];
  rpcCalls: Array<{ name: string; parameters?: Record<string, unknown> }>;
  listCalls: Array<{ prefix: string; limit: number; offset: number }>;
}

function clientFixture(options: {
  userId?: string | null;
  userError?: unknown;
  deleteError?: unknown | (() => unknown);
  rpcError?: unknown | (() => unknown);
  buckets?: string[];
  list?: (
    prefix: string,
    offset: number,
    limit: number,
  ) => Array<{ id?: string | null; name: string }>;
  listError?: unknown;
  removeError?: unknown | (() => unknown);
  onDelete?: (id: string) => void;
  onRemove?: (paths: string[]) => void;
  onRpc?: (name: string, parameters?: Record<string, unknown>) => void;
} = {}): ClientFixture {
  const deletedUsers: string[] = [];
  const removedBatches: string[][] = [];
  const rpcCalls: Array<{ name: string; parameters?: Record<string, unknown> }> = [];
  const listCalls: Array<{ prefix: string; limit: number; offset: number }> = [];
  const storageBucket = {
    list: (prefix: string, request: { limit: number; offset: number }) => {
      listCalls.push({ prefix, limit: request.limit, offset: request.offset });
      return Promise.resolve({
        data: options.list?.(prefix, request.offset, request.limit) ?? [],
        error: options.listError ?? null,
      });
    },
    remove: (paths: string[]) => {
      removedBatches.push(paths);
      const error = typeof options.removeError === "function"
        ? options.removeError()
        : options.removeError;
      if (!error) options.onRemove?.(paths);
      return Promise.resolve({ error: error ?? null });
    },
  };
  return {
    client: {
      rpc: (name, parameters) => {
        rpcCalls.push({ name, parameters });
        options.onRpc?.(name, parameters);
        const error = typeof options.rpcError === "function"
          ? options.rpcError()
          : options.rpcError;
        return Promise.resolve({
          data: error ? null : [{ tombstoned: true }],
          error: error ?? null,
        });
      },
      auth: {
        getUser: () =>
          Promise.resolve({
            data: {
              user: options.userId === null ? null : { id: options.userId ?? "auth-user-1" },
            },
            error: options.userError ?? null,
          }),
        admin: {
          deleteUser: (id: string) => {
            deletedUsers.push(id);
            options.onDelete?.(id);
            const error = typeof options.deleteError === "function"
              ? options.deleteError()
              : options.deleteError;
            return Promise.resolve({ error: error ?? null });
          },
        },
      },
      storage: {
        listBuckets: () =>
          Promise.resolve({
            data: (options.buckets ?? ["receipts"]).map((name) => ({ name })),
            error: null,
          }),
        from: () => storageBucket,
      },
    },
    deletedUsers,
    removedBatches,
    rpcCalls,
    listCalls,
  };
}

function dependencies(options: {
  env?: Record<string, string>;
  user?: ClientFixture;
  admin?: ClientFixture;
  cleanupResponse?: Response;
  cleanupError?: Error;
} = {}) {
  const user = options.user ?? clientFixture();
  const admin = options.admin ?? clientFixture();
  const fetches: Array<{ input: string; init?: RequestInit }> = [];
  const logs: string[] = [];
  const createdKeys: string[] = [];
  const env = options.env ?? BASE_ENV;

  const value: DeleteAccountDependencies = {
    getEnv: (name) => env[name],
    fetch: (input, init) => {
      fetches.push({ input: input.toString(), init });
      if (options.cleanupError) return Promise.reject(options.cleanupError);
      return Promise.resolve(options.cleanupResponse ?? new Response(null, { status: 204 }));
    },
    createClient: (_url, key) => {
      createdKeys.push(key);
      return key === env.SUPABASE_ANON_KEY ? user.client : admin.client;
    },
    logError: (message) => logs.push(message),
    createRequestId: () => GENERATED_REQUEST_ID,
  };

  return { value, user, admin, fetches, logs, createdKeys };
}

function postRequest(headers: Record<string, string> = {}): Request {
  return new Request("https://edge.test", {
    method: "POST",
    headers: { authorization: "Bearer valid-token", ...headers },
  });
}

async function responseError(response: Response): Promise<string> {
  return (await response.json() as { error: string }).error;
}

function assertCorsAndRequestId(response: Response, requestId = GENERATED_REQUEST_ID) {
  assert.equal(response.headers.get("access-control-allow-origin"), "*");
  assert.equal(response.headers.get("access-control-allow-methods"), "POST, OPTIONS");
  assert.equal(response.headers.get("x-request-id"), requestId);
}

function mutableTree(
  initial: Record<string, Array<{ id?: string | null; name: string }>>,
) {
  const entries = new Map(
    Object.entries(initial).map(([prefix, values]) => [prefix, [...values]]),
  );
  return {
    list: (prefix: string, offset: number, limit: number) =>
      (entries.get(prefix) ?? []).slice(offset, offset + limit),
    remove: (paths: string[]) => {
      for (const path of paths) {
        const separator = path.lastIndexOf("/");
        const prefix = path.slice(0, separator);
        const name = path.slice(separator + 1);
        entries.set(
          prefix,
          (entries.get(prefix) ?? []).filter((entry) => entry.name !== name),
        );
      }
    },
    remainingFiles: () => [...entries.values()].flat().filter((entry) => entry.id != null).length,
  };
}

Deno.test("bearerToken accepts one non-whitespace token and rejects malformed values", () => {
  assert.equal(bearerToken("Bearer abc.def"), "abc.def");
  assert.equal(bearerToken("bearer\tabc "), "abc");
  assert.equal(bearerToken("Bearer two tokens"), null);
  assert.equal(bearerToken("Basic abc"), null);
  assert.equal(bearerToken(null), null);
});

Deno.test("handler answers CORS preflight and rejects unsupported methods before secrets", async () => {
  const fixture = dependencies({ env: {} });
  const handler = createDeleteAccountHandler(fixture.value);

  const preflight = await handler(new Request("https://edge.test", { method: "OPTIONS" }));
  assert.equal(preflight.status, 204);
  assertCorsAndRequestId(preflight);

  const response = await handler(new Request("https://edge.test", { method: "GET" }));
  assert.equal(response.status, 405);
  assert.equal(await responseError(response), "Method not allowed");
  assert.equal(response.headers.get("allow"), "POST, OPTIONS");
  assertCorsAndRequestId(response);
  assert.deepEqual(fixture.createdKeys, []);
});

Deno.test("handler fails closed with a structured PII-free configuration log", async () => {
  const fixture = dependencies({ env: { SUPABASE_URL: BASE_ENV.SUPABASE_URL } });
  const response = await createDeleteAccountHandler(fixture.value)(postRequest({
    "x-request-id": "not-safe personal@example.test",
  }));

  assert.equal(response.status, 500);
  assert.equal(await responseError(response), "Server configuration error");
  assertCorsAndRequestId(response);
  assert.deepEqual(JSON.parse(fixture.logs[0]), {
    event: "delete_account_failed",
    request_id: GENERATED_REQUEST_ID,
    stage: "configuration",
  });
  assert.ok(!fixture.logs[0].includes("personal@example.test"));
  assert.deepEqual(fixture.createdKeys, []);
});

Deno.test("handler rejects missing and invalid JWTs before creating an admin client", async () => {
  const missing = dependencies();
  const missingResponse = await createDeleteAccountHandler(missing.value)(
    new Request("https://edge.test", { method: "POST" }),
  );
  assert.equal(missingResponse.status, 401);
  assertCorsAndRequestId(missingResponse);
  assert.deepEqual(missing.createdKeys, []);

  const invalid = dependencies({
    user: clientFixture({ userId: null, userError: new Error("invalid JWT") }),
  });
  const invalidResponse = await createDeleteAccountHandler(invalid.value)(postRequest());
  assert.equal(invalidResponse.status, 401);
  assert.deepEqual(invalid.createdKeys, [BASE_ENV.SUPABASE_ANON_KEY]);
  assert.deepEqual(invalid.admin.deletedUsers, []);
  assert.deepEqual(invalid.admin.rpcCalls, []);
});

Deno.test("handler tombstones before external, Storage and Auth cleanup", async () => {
  const order: string[] = [];
  const tree = mutableTree({
    "auth-user-7": [
      { id: null, name: "nested" },
      { id: "file-1", name: "receipt.jpg" },
    ],
    "auth-user-7/nested": [{ id: "file-2", name: "audio.ogg" }],
  });
  const user = clientFixture({ userId: "auth-user-7" });
  const admin = clientFixture({
    list: tree.list,
    onRpc: () => order.push("tombstone"),
    onRemove: (paths) => {
      order.push("remove-receipts");
      tree.remove(paths);
    },
    onDelete: (id) => order.push(`delete-user:${id}`),
  });
  const fixture = dependencies({ user, admin });
  const originalFetch = fixture.value.fetch;
  fixture.value.fetch = (input, init) => {
    order.push("external-cleanup");
    return originalFetch(input, init);
  };

  const response = await createDeleteAccountHandler(fixture.value)(postRequest({
    "x-request-id": SUPPLIED_REQUEST_ID,
  }));

  assert.equal(response.status, 204);
  assertCorsAndRequestId(response, SUPPLIED_REQUEST_ID);
  assert.deepEqual(admin.rpcCalls, [{
    name: "begin_account_deletion",
    parameters: { p_auth_id: "auth-user-7" },
  }]);
  assert.equal(fixture.fetches.length, 1);
  assert.equal(fixture.fetches[0].input, BASE_ENV.ACCOUNT_DELETION_CLEANUP_URL);
  assert.equal(fixture.fetches[0].init?.redirect, "error");
  const cleanupHeaders = new Headers(fixture.fetches[0].init?.headers);
  assert.equal(cleanupHeaders.get("authorization"), "Bearer cleanup-secret");
  assert.equal(cleanupHeaders.get("x-request-id"), SUPPLIED_REQUEST_ID);
  assert.deepEqual(
    JSON.parse(fixture.fetches[0].init?.body as string),
    { auth_user_id: "auth-user-7" },
  );
  assert.deepEqual(admin.removedBatches, [
    ["auth-user-7/receipt.jpg"],
    ["auth-user-7/nested/audio.ogg"],
  ]);
  assert.deepEqual(admin.deletedUsers, ["auth-user-7"]);
  assert.deepEqual(order, [
    "tombstone",
    "external-cleanup",
    "remove-receipts",
    "remove-receipts",
    "delete-user:auth-user-7",
  ]);
});

Deno.test("tombstone failure stops every destructive external step", async () => {
  const admin = clientFixture({ rpcError: new Error("database unavailable") });
  const fixture = dependencies({ admin });
  const response = await createDeleteAccountHandler(fixture.value)(postRequest());

  assert.equal(response.status, 500);
  assert.equal(await responseError(response), "Account deletion failed");
  assert.deepEqual(fixture.fetches, []);
  assert.deepEqual(admin.removedBatches, []);
  assert.deepEqual(admin.deletedUsers, []);
  assert.deepEqual(JSON.parse(fixture.logs[0]), {
    event: "delete_account_failed",
    request_id: GENERATED_REQUEST_ID,
    stage: "tombstone",
  });
});

Deno.test("external cleanup configuration fails after tombstone but before Storage/Auth", async () => {
  const env = { ...BASE_ENV };
  delete env.ACCOUNT_DELETION_CLEANUP_SECRET;
  const fixture = dependencies({ env });

  const response = await createDeleteAccountHandler(fixture.value)(postRequest());

  assert.equal(response.status, 500);
  assert.equal(await responseError(response), "Account deletion failed");
  assert.equal(fixture.admin.rpcCalls.length, 1);
  assert.deepEqual(fixture.admin.removedBatches, []);
  assert.deepEqual(fixture.admin.deletedUsers, []);
  assert.equal(JSON.parse(fixture.logs[0]).stage, "external_cleanup");
});

Deno.test("handler rejects insecure cleanup destinations without sending the secret", async () => {
  const fixture = dependencies({
    env: { ...BASE_ENV, ACCOUNT_DELETION_CLEANUP_URL: "http://cleanup.example.test" },
  });

  const response = await createDeleteAccountHandler(fixture.value)(postRequest());

  assert.equal(response.status, 500);
  assert.equal(fixture.admin.rpcCalls.length, 1);
  assert.deepEqual(fixture.fetches, []);
  assert.deepEqual(fixture.admin.deletedUsers, []);
});

Deno.test("deleteReceiptObjects paginates iteratively and removes bounded batches", async () => {
  const rootFiles = Array.from({ length: 205 }, (_, index) => ({
    id: `id-${index}`,
    name: `receipt-${index.toString().padStart(3, "0")}.jpg`,
  }));
  const tree = mutableTree({
    "auth-user-1": [
      { id: null, name: "00-nested" },
      ...rootFiles,
      { id: null, name: "zz-nested" },
    ],
    "auth-user-1/00-nested": [{ id: "nested-a", name: "a.jpg" }],
    "auth-user-1/zz-nested": [{ id: "nested-z", name: "z.jpg" }],
  });
  const fixture = clientFixture({ list: tree.list, onRemove: tree.remove });

  await deleteReceiptObjects(fixture.client, "auth-user-1");

  assert.equal(tree.remainingFiles(), 0);
  assert.equal(fixture.removedBatches.flat().length, 207);
  assert.ok(fixture.removedBatches.every((batch) => batch.length <= 100));
  assert.ok(fixture.listCalls.every((call) => call.limit === 100));
  assert.ok(fixture.listCalls.some((call) => call.prefix === "auth-user-1/00-nested"));
  assert.ok(fixture.listCalls.some((call) => call.prefix === "auth-user-1/zz-nested"));
});

Deno.test("deleteReceiptObjects treats an absent receipts bucket as already clean", async () => {
  const fixture = clientFixture({ buckets: [] });

  await deleteReceiptObjects(fixture.client, "auth-user-1");

  assert.deepEqual(fixture.listCalls, []);
  assert.deepEqual(fixture.removedBatches, []);
});

Deno.test("a partial Storage failure is safely retryable", async () => {
  const tree = mutableTree({
    "auth-user-1": Array.from({ length: 150 }, (_, index) => ({
      id: `id-${index}`,
      name: `receipt-${index.toString().padStart(3, "0")}.jpg`,
    })),
  });
  let removalAttempts = 0;
  const admin = clientFixture({
    list: tree.list,
    removeError: () => ++removalAttempts === 2 ? new Error("partial outage") : null,
    onRemove: tree.remove,
  });
  const fixture = dependencies({ admin });
  const handler = createDeleteAccountHandler(fixture.value);

  const first = await handler(postRequest());
  assert.equal(first.status, 500);
  assert.equal(JSON.parse(fixture.logs[0]).stage, "storage_cleanup");
  assert.equal(tree.remainingFiles(), 50);
  assert.deepEqual(admin.deletedUsers, []);

  const retry = await handler(postRequest());
  assert.equal(retry.status, 204);
  assert.equal(tree.remainingFiles(), 0);
  assert.deepEqual(admin.deletedUsers, ["auth-user-1"]);
  assert.equal(admin.rpcCalls.length, 2);
  assert.equal(fixture.fetches.length, 2);
});

Deno.test("an Auth deletion failure is reported with only stage and correlation id", async () => {
  const fixture = dependencies({
    admin: clientFixture({ deleteError: new Error("provider secret details") }),
  });
  const response = await createDeleteAccountHandler(fixture.value)(postRequest());

  assert.equal(response.status, 500);
  assert.equal(await responseError(response), "Account deletion failed");
  assert.deepEqual(JSON.parse(fixture.logs.at(-1)!), {
    event: "delete_account_failed",
    request_id: GENERATED_REQUEST_ID,
    stage: "auth_delete",
  });
  assert.ok(!fixture.logs.at(-1)!.includes("provider secret details"));
});
