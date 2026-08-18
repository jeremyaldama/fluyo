import {
  disconnectEmailGrantForUser,
  getGrantByEmail,
  type GmailGrantMetadata,
  markGrantError,
  resolvePublicUserId,
  SupabaseServiceError,
  updateGrantWatch,
  upsertEmailGrant,
  verifySupabaseAuthId,
} from "../_shared/db.ts";
import {
  exchangeAuthorizationCode,
  getGmailProfile,
  gmailWatchExpirationIso,
  GoogleApiError,
  startWatch,
} from "../_shared/gmail-client.ts";
import {
  buildOAuthCompletionRedirect,
  GmailConnectContractError,
  type GmailConnectRequest,
  MAX_CONNECT_BODY_BYTES,
  parseGmailConnectRequest,
  resolvePublicOAuthCallback,
  runForOAuthStateOwner,
} from "../_shared/gmail-connect-contract.ts";
import { createPkcePair, openOAuthState, sealOAuthState } from "../_shared/oauth-state.ts";
import { gmailMailboxFingerprint } from "../_shared/gmail-reference.ts";

const GMAIL_CLIENT_ID = Deno.env.get("GMAIL_CLIENT_ID") ?? "";
const GMAIL_CLIENT_SECRET = Deno.env.get("GMAIL_CLIENT_SECRET") ?? "";
const PUBSUB_TOPIC = Deno.env.get("GOOGLE_PUBSUB_TOPIC") ?? "";
const STATE_SECRET = Deno.env.get("GMAIL_OAUTH_STATE_SECRET") ?? "";
const DEDUPE_SECRET = Deno.env.get("GMAIL_DEDUPE_SECRET") ?? "";
const AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const GMAIL_READONLY_SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
const DEFAULT_APP_REDIRECT_URI = "com.qolve.fluyo://gmail-callback";

type CallbackErrorCode =
  | "access_denied"
  | "invalid_state"
  | "state_expired"
  | "server_error";

type CompletionErrorCode =
  | "invalid_request"
  | "invalid_state"
  | "state_expired"
  | "state_user_mismatch"
  | "unauthorized"
  | "user_not_found"
  | "oauth_exchange_failed"
  | "missing_scope"
  | "profile_failed"
  | "watch_failed"
  | "account_conflict"
  | "grant_store_failed"
  | "server_not_configured"
  | "server_error";

function noStoreHeaders(extra: HeadersInit = {}): Headers {
  const headers = new Headers(extra);
  headers.set("Cache-Control", "no-store");
  headers.set("Pragma", "no-cache");
  headers.set("Referrer-Policy", "no-referrer");
  headers.set("X-Content-Type-Options", "nosniff");
  return headers;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: noStoreHeaders({ "Content-Type": "application/json; charset=utf-8" }),
  });
}

function googleRedirectUri(req: Request): string {
  const url = new URL(req.url);
  url.search = "";
  url.hash = "";
  url.pathname = url.pathname.replace(/\/+$/, "");
  return url.toString();
}

function allowedAppRedirectUris(): Set<string> {
  const configured = Deno.env.get("GMAIL_OAUTH_ALLOWED_REDIRECT_URIS") ?? DEFAULT_APP_REDIRECT_URI;
  const values = configured.split(",").map((value) => value.trim()).filter(Boolean);
  const allowed = new Set<string>();
  for (const value of values) {
    try {
      const url = new URL(value);
      if (url.username || url.password || url.hash) continue;
      allowed.add(url.toString());
    } catch {
      // Invalid configuration is ignored; an empty allowlist fails closed.
    }
  }
  return allowed;
}

function validateAppRedirectUri(value: unknown): string {
  const candidate = typeof value === "string" && value ? value : DEFAULT_APP_REDIRECT_URI;
  let normalized: string;
  try {
    normalized = new URL(candidate).toString();
  } catch {
    throw new SupabaseServiceError("redirect_uri", 400, "invalid_redirect_uri");
  }
  if (!allowedAppRedirectUris().has(normalized)) {
    throw new SupabaseServiceError("redirect_uri", 400, "invalid_redirect_uri");
  }
  return normalized;
}

async function readJsonObject(req: Request, allowEmpty: boolean): Promise<Record<string, unknown>> {
  const contentLength = Number(req.headers.get("content-length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > MAX_CONNECT_BODY_BYTES) {
    throw new SupabaseServiceError("request_body", 413, "body_too_large");
  }
  const text = await req.text();
  if (new TextEncoder().encode(text).byteLength > MAX_CONNECT_BODY_BYTES) {
    throw new SupabaseServiceError("request_body", 413, "body_too_large");
  }
  if (!text.trim() && allowEmpty) return {};
  try {
    const parsed = JSON.parse(text);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error();
    return parsed;
  } catch {
    throw new SupabaseServiceError("request_body", 400, "invalid_json");
  }
}

function appErrorRedirect(base: string, code: CallbackErrorCode): Response {
  const url = new URL(base);
  url.search = "";
  url.hash = "";
  url.searchParams.set("status", "error");
  url.searchParams.set("code", code);
  return new Response(null, { status: 302, headers: noStoreHeaders({ Location: url.toString() }) });
}

function appCompletionRedirect(base: string, authorizationCode: string, state: string): Response {
  return new Response(null, {
    status: 302,
    headers: noStoreHeaders({ Location: buildOAuthCompletionRedirect(base, authorizationCode, state) }),
  });
}

function completionError(code: CompletionErrorCode, status: number): Response {
  return jsonResponse({ error: code }, status);
}

function oauthServerConfigured(): boolean {
  return Boolean(
    GMAIL_CLIENT_ID && GMAIL_CLIENT_SECRET && PUBSUB_TOPIC &&
      new TextEncoder().encode(STATE_SECRET).byteLength >= 32 &&
      new TextEncoder().encode(DEDUPE_SECRET).byteLength >= 32,
  );
}

async function handleInit(req: Request, redirectUri: string | undefined): Promise<Response> {
  if (!oauthServerConfigured()) return jsonResponse({ error: "server_not_configured" }, 503);
  const appRedirectUri = validateAppRedirectUri(redirectUri);
  const authId = await verifySupabaseAuthId(req);
  const userId = await resolvePublicUserId(authId);
  const pkce = await createPkcePair();
  const state = await sealOAuthState(STATE_SECRET, {
    userId,
    redirectUri: appRedirectUri,
    codeVerifier: pkce.verifier,
  });

  const authorizationUrl = new URL(AUTH_URL);
  authorizationUrl.searchParams.set("client_id", GMAIL_CLIENT_ID);
  authorizationUrl.searchParams.set("redirect_uri", googleRedirectUri(req));
  authorizationUrl.searchParams.set("response_type", "code");
  authorizationUrl.searchParams.set("scope", GMAIL_READONLY_SCOPE);
  authorizationUrl.searchParams.set("access_type", "offline");
  authorizationUrl.searchParams.set("include_granted_scopes", "true");
  authorizationUrl.searchParams.set("prompt", "consent select_account");
  authorizationUrl.searchParams.set("state", state);
  authorizationUrl.searchParams.set("code_challenge", pkce.challenge);
  authorizationUrl.searchParams.set("code_challenge_method", "S256");

  return jsonResponse({ authorization_url: authorizationUrl.toString(), expires_in: 600 });
}

async function handleCallback(url: URL): Promise<Response> {
  if (new TextEncoder().encode(STATE_SECRET).byteLength < 32) {
    return appErrorRedirect(DEFAULT_APP_REDIRECT_URI, "server_error");
  }

  const outcome = await resolvePublicOAuthCallback(
    url.searchParams,
    DEFAULT_APP_REDIRECT_URI,
    (state) => openOAuthState(STATE_SECRET, state),
  );
  if (outcome.kind === "error") {
    if (outcome.code === "invalid_state" || outcome.code === "state_expired") {
      console.warn(`gmail-connect callback rejected:${outcome.code}`);
    }
    return appErrorRedirect(outcome.redirectUri, outcome.code);
  }

  // Public callback ends here. No token exchange, watch, Vault or DB call is
  // allowed until the authenticated app proves it owns state.userId.
  return appCompletionRedirect(outcome.redirectUri, outcome.authorizationCode, outcome.state);
}

function completionAuthError(error: unknown): Response {
  if (error instanceof SupabaseServiceError) {
    if (error.operation === "auth_user" && error.status === 401) return completionError("unauthorized", 401);
    if (error.operation === "resolve_user" && error.status === 404) return completionError("user_not_found", 404);
  }
  console.error(`gmail-connect completion auth:${safeOperation(error)}`);
  return completionError("server_error", 503);
}

async function handleCompletion(
  req: Request,
  request: Extract<GmailConnectRequest, { kind: "complete" }>,
): Promise<Response> {
  let userId: string;
  try {
    const authId = await verifySupabaseAuthId(req);
    userId = await resolvePublicUserId(authId);
  } catch (error) {
    return completionAuthError(error);
  }

  if (!oauthServerConfigured()) return completionError("server_not_configured", 503);

  let state;
  try {
    state = await openOAuthState(STATE_SECRET, request.state);
  } catch (error) {
    return completionError(
      error instanceof Error && error.message === "expired_state" ? "state_expired" : "invalid_state",
      400,
    );
  }

  try {
    return await runForOAuthStateOwner(
      state.userId,
      userId,
      () => completeAuthorizedOAuth(req, userId, request.authorizationCode, state.codeVerifier),
    );
  } catch (error) {
    if (error instanceof GmailConnectContractError && error.code === "state_user_mismatch") {
      return completionError("state_user_mismatch", 403);
    }
    console.error(`gmail-connect completion:${safeOperation(error)}`);
    return completionError("server_error", 500);
  }
}

async function completeAuthorizedOAuth(
  req: Request,
  userId: string,
  authorizationCode: string,
  codeVerifier: string,
): Promise<Response> {
  let tokens;
  try {
    tokens = await exchangeAuthorizationCode(authorizationCode, googleRedirectUri(req), codeVerifier);
  } catch (error) {
    console.error(`gmail-connect token exchange:${safeOperation(error)}`);
    return completionError(
      "oauth_exchange_failed",
      error instanceof GoogleApiError && error.retryable ? 503 : 400,
    );
  }
  if (tokens.grantedScopes.length > 0 && !tokens.grantedScopes.includes(GMAIL_READONLY_SCOPE)) {
    return completionError("missing_scope", 403);
  }

  let profile;
  try {
    profile = await getGmailProfile(tokens.accessToken);
  } catch (error) {
    console.error(`gmail-connect profile:${safeOperation(error)}`);
    return completionError(
      "profile_failed",
      error instanceof GoogleApiError && error.retryable ? 503 : 400,
    );
  }
  const gmailAddress = profile.emailAddress;

  let mailboxFingerprint: string;
  try {
    mailboxFingerprint = await gmailMailboxFingerprint(gmailAddress);
    const mailboxOwner = await getGrantByEmail(gmailAddress);
    if (mailboxOwner && mailboxOwner.user_id !== userId) {
      // Do not revoke the just-issued token: Google revocation can invalidate
      // the existing owner's grant for this same OAuth client.
      return completionError("account_conflict", 409);
    }
  } catch (error) {
    console.error(`gmail-connect existing grant:${safeOperation(error)}`);
    return completionError("grant_store_failed", 503);
  }

  // Persist a pre-watch history baseline before enabling notifications. Gmail
  // sends an immediate notification for a successful watch(); with this order,
  // even that first push always finds a durable grant and can process H0..H1.
  let storedGrant: GmailGrantMetadata | null = null;
  try {
    storedGrant = await upsertEmailGrant({
      userId,
      email: gmailAddress,
      refreshToken: tokens.refreshToken,
      mailboxFingerprint,
      historyId: profile.historyId,
      watchExpiration: null,
    });
  } catch (error) {
    console.error(`gmail-connect grant storage:${safeOperation(error)}`);
    const conflict = error instanceof SupabaseServiceError &&
      (error.status === 409 || error.code === "23505" || error.code === "gmail_account_already_linked");
    if (conflict) {
      // Do not stop/revoke: a concurrent owner may now rely on this mailbox's
      // single Gmail watch and OAuth client grant.
      return completionError("account_conflict", 409);
    }

    // A timeout can happen after PostgreSQL committed. Confirm before returning;
    // token compensation is intentionally avoided because it can race a
    // concurrent winning relink for the same Google authorization.
    try {
      const observed = await getGrantByEmail(gmailAddress);
      if (observed?.user_id === userId && observed.mailbox_fingerprint === mailboxFingerprint) {
        storedGrant = {
          grant_id: observed.grant_id,
          email: observed.email,
          history_id: observed.history_id,
          watch_expiration: observed.watch_expiration,
        };
      }
    } catch {
      // Ambiguous storage state: never revoke a possibly committed or
      // concurrently reauthorized Google grant.
    }
    if (!storedGrant) {
      return completionError("grant_store_failed", 503);
    }
  }

  if (!storedGrant) return completionError("grant_store_failed", 503);

  let watch;
  try {
    watch = await startWatch(tokens.accessToken, PUBSUB_TOPIC);
  } catch (error) {
    console.error(`gmail-connect watch:${safeOperation(error)}`);
    // Keep the durable token/grant. A NULL expiration makes it immediately due
    // for gmail-renew, while the app exposes a safe attention state.
    try {
      await markGrantError(storedGrant.grant_id, "watch_failed");
    } catch {
      // The missing expiration still keeps this grant recoverable by cron.
    }
    return completionError("watch_failed", 503);
  }

  try {
    const stillActive = await updateGrantWatch(
      storedGrant.grant_id,
      gmailWatchExpirationIso(watch.expiration),
      watch.historyId,
    );
    if (!stillActive) {
      // A concurrent disconnect/relink won after the grant was stored. Do not
      // report success. Its winning flow owns external cleanup; compensating
      // here could revoke a concurrently reauthorized grant for the mailbox.
      return completionError("grant_store_failed", 409);
    }
  } catch (error) {
    console.error(`gmail-connect watch storage:${safeOperation(error)}`);
    // The database result may be ambiguous after a timeout. Preserve the
    // durable grant/token/watch (and avoid external cleanup from this ambiguous
    // flow) so metadata refresh and cron can reconcile it safely.
    return completionError("grant_store_failed", 503);
  }

  return jsonResponse({ status: "success" });
}

async function handleDisconnect(req: Request): Promise<Response> {
  const body = await readJsonObject(req, true);
  if (Object.keys(body).length !== 0) return jsonResponse({ error: "invalid_request" }, 400);
  const authId = await verifySupabaseAuthId(req);
  const userId = await resolvePublicUserId(authId);
  // This single RPC deletes the row and Vault secret without decrypting the
  // refresh token into Edge memory. Orphaned pushes fail closed and the Gmail
  // watch expires naturally.
  const disconnected = await disconnectEmailGrantForUser(userId);
  return jsonResponse({ disconnected: disconnected ? 1 : 0 });
}

function safeOperation(error: unknown): string {
  if (error instanceof GoogleApiError || error instanceof SupabaseServiceError) {
    return `${error.operation}:${error.status}:${error.code}`;
  }
  return error instanceof Error && /^[a-zA-Z0-9_.:-]{1,128}$/.test(error.message) ? error.message : "unknown_error";
}

Deno.serve(async (req: Request) => {
  const url = new URL(req.url);
  try {
    if (req.method === "POST") {
      if (["token", "code", "state", "error"].some((key) => url.searchParams.has(key))) {
        return completionError("invalid_request", 400);
      }
      let body: Record<string, unknown>;
      try {
        body = await readJsonObject(req, true);
      } catch (error) {
        const status = error instanceof SupabaseServiceError && error.status === 413 ? 413 : 400;
        return completionError("invalid_request", status);
      }
      let request: GmailConnectRequest;
      try {
        request = parseGmailConnectRequest(body);
      } catch (error) {
        if (error instanceof GmailConnectContractError) return completionError("invalid_request", 400);
        throw error;
      }
      return request.kind === "init"
        ? await handleInit(req, request.redirectUri)
        : await handleCompletion(req, request);
    }
    if (req.method === "GET" && (url.searchParams.has("code") || url.searchParams.has("error"))) {
      return await handleCallback(url);
    }
    if (req.method === "DELETE") return await handleDisconnect(req);
    return jsonResponse({ error: "method_not_allowed" }, 405);
  } catch (error) {
    if (error instanceof SupabaseServiceError) {
      const status = [400, 401, 403, 404, 413].includes(error.status) ? error.status : 503;
      return jsonResponse({ error: error.code }, status);
    }
    console.error(`gmail-connect request:${safeOperation(error)}`);
    return jsonResponse({ error: "server_error" }, 500);
  }
});
