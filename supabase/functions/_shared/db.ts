const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";
const REQUEST_TIMEOUT_MS = 12_000;

export type GrantErrorCode =
  | "watch_failed"
  | "token_refresh_failed"
  | "gmail_api_failed"
  | "webhook_failed"
  | "relink_required";

export interface EmailGrantRow {
  grant_id: string;
  user_id: string;
  email: string;
  mailbox_fingerprint?: string | null;
  refresh_token: string | null;
  history_id: string | null;
  watch_expiration: string | null;
  last_synced_at?: string | null;
  created_at?: string | null;
}

export interface GmailGrantMetadata {
  grant_id: string;
  email: string;
  history_id: string | null;
  watch_expiration: string | null;
}

export class SupabaseServiceError extends Error {
  constructor(
    public readonly operation: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(`${operation}_failed:${status}:${code}`);
    this.name = "SupabaseServiceError";
  }
}

function ensureConfig(requireAnon = false): void {
  if (!SUPABASE_URL || !SERVICE_ROLE_KEY || (requireAnon && !ANON_KEY)) {
    throw new Error("supabase_not_configured");
  }
}

function serviceHeaders(extra: HeadersInit = {}): Headers {
  ensureConfig();
  const headers = new Headers(extra);
  headers.set("apikey", SERVICE_ROLE_KEY);
  headers.set("Authorization", `Bearer ${SERVICE_ROLE_KEY}`);
  return headers;
}

async function safeSupabaseErrorCode(response: Response): Promise<string> {
  try {
    const parsed = JSON.parse((await response.text()).slice(0, 16_384)) as Record<string, unknown>;
    for (const field of ["code", "message"]) {
      const value = parsed[field];
      if (typeof value === "string" && /^[a-zA-Z0-9_.:-]{1,128}$/.test(value)) return value;
    }
  } catch {
    // Avoid reflecting arbitrary database/proxy output into logs or clients.
  }
  return "database_error";
}

async function dbFetch(operation: string, input: string | URL, init: RequestInit): Promise<Response> {
  let response: Response;
  try {
    response = await fetch(input, { ...init, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
  } catch {
    throw new SupabaseServiceError(operation, 503, "network_error");
  }
  if (!response.ok) {
    throw new SupabaseServiceError(operation, response.status, await safeSupabaseErrorCode(response));
  }
  return response;
}

async function rpc<T>(operation: string, body: Record<string, unknown>): Promise<T> {
  const response = await dbFetch(operation, `${SUPABASE_URL}/rest/v1/rpc/${operation}`, {
    method: "POST",
    headers: serviceHeaders({ "Content-Type": "application/json" }),
    body: JSON.stringify(body),
  });
  try {
    return await response.json() as T;
  } catch {
    throw new SupabaseServiceError(operation, 502, "invalid_json");
  }
}

export function bearerToken(req: Request): string | null {
  const authorization = req.headers.get("authorization") ?? "";
  const match = authorization.match(/^Bearer ([^\s]{16,16384})$/i);
  return match?.[1] ?? null;
}

/** Verify the app JWT with Supabase Auth; never infer identity from decoded claims. */
export async function verifySupabaseAuthId(req: Request): Promise<string> {
  ensureConfig(true);
  const token = bearerToken(req);
  if (!token) throw new SupabaseServiceError("auth_user", 401, "missing_bearer_token");
  let response: Response;
  try {
    response = await fetch(`${SUPABASE_URL}/auth/v1/user`, {
      headers: { apikey: ANON_KEY, Authorization: `Bearer ${token}` },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch {
    throw new SupabaseServiceError("auth_user", 503, "network_error");
  }
  if (!response.ok) throw new SupabaseServiceError("auth_user", 401, "invalid_session");
  const user = await response.json() as Record<string, unknown>;
  if (typeof user.id !== "string" || !isUuid(user.id)) {
    throw new SupabaseServiceError("auth_user", 401, "invalid_session");
  }
  return user.id;
}

export async function resolvePublicUserId(authId: string): Promise<string> {
  if (!isUuid(authId)) throw new Error("invalid_auth_id");
  const url = new URL(`${SUPABASE_URL}/rest/v1/users`);
  url.searchParams.set("auth_id", `eq.${authId}`);
  url.searchParams.set("select", "id");
  url.searchParams.set("limit", "1");
  const response = await dbFetch("resolve_user", url, {
    headers: serviceHeaders({ Accept: "application/json" }),
  });
  const rows = await response.json() as Array<{ id?: unknown }>;
  const id = rows[0]?.id;
  if (typeof id !== "string" || !isUuid(id)) {
    throw new SupabaseServiceError("resolve_user", 404, "user_not_found");
  }
  return id;
}

export async function upsertEmailGrant(input: {
  userId: string;
  email: string;
  refreshToken: string;
  mailboxFingerprint: string;
  historyId: string | null;
  watchExpiration: string | null;
}): Promise<GmailGrantMetadata> {
  const rows = await rpc<GmailGrantMetadata[]>("upsert_email_grant", {
    p_user_id: input.userId,
    p_email: input.email,
    p_mailbox_fingerprint: input.mailboxFingerprint,
    p_refresh_token: input.refreshToken,
    p_history_id: input.historyId,
    p_watch_expiration: input.watchExpiration,
  });
  if (!rows[0]?.grant_id) throw new SupabaseServiceError("upsert_email_grant", 502, "empty_result");
  return rows[0];
}

export async function getGrantByEmail(email: string): Promise<EmailGrantRow | null> {
  const rows = await rpc<EmailGrantRow[]>("get_email_grant_for_sync", { p_email: email });
  return rows[0] ?? null;
}

export function listGrantsDueForRenewal(beforeIso: string): Promise<EmailGrantRow[]> {
  return rpc<EmailGrantRow[]>("list_email_grants_due_for_renewal", { p_before: beforeIso });
}

export function advanceGrantCursor(grantId: string, historyId: string): Promise<boolean> {
  return rpc<boolean>("advance_email_grant_cursor", {
    grant_id: grantId,
    new_history_id: historyId,
    synced_at: new Date().toISOString(),
  });
}

export function updateGrantWatch(
  grantId: string,
  expirationIso: string,
  initialHistoryId: string | null = null,
): Promise<boolean> {
  return rpc<boolean>("update_email_grant_watch", {
    grant_id: grantId,
    watch_expiration: expirationIso,
    initial_history_id: initialHistoryId,
  });
}

export async function markGrantError(grantId: string, errorCode: GrantErrorCode): Promise<void> {
  await rpc<boolean>("mark_email_grant_sync_error", {
    grant_id: grantId,
    error_message: errorCode,
  });
}

export function disconnectEmailGrantForUser(userId: string): Promise<boolean> {
  return rpc<boolean>("disconnect_email_grant_for_user", { p_user_id: userId });
}

export type EmailExpenseInsertStatus = "inserted" | "duplicate" | "inactive";

export interface InsertEmailExpenseInput {
  grant_id: string;
  message_id: string;
  amount: number;
  description: string | null;
  expense_date: string;
  recipient: string | null;
}

/** Atomic authorization + category resolution + idempotent insert. */
export async function insertEmailExpenseIfGrantActive(
  input: InsertEmailExpenseInput,
): Promise<EmailExpenseInsertStatus> {
  const status = await rpc<string>("insert_email_expense_if_grant_active", {
    p_grant_id: input.grant_id,
    p_message_id: input.message_id,
    p_amount: input.amount,
    p_description: input.description,
    p_expense_date: input.expense_date,
    p_recipient: input.recipient,
  });
  if (status !== "inserted" && status !== "duplicate" && status !== "inactive") {
    throw new SupabaseServiceError("insert_email_expense_if_grant_active", 502, "invalid_result");
  }
  return status;
}

function isUuid(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value);
}
