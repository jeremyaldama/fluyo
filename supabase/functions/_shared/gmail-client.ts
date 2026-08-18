const GMAIL_CLIENT_ID = Deno.env.get("GMAIL_CLIENT_ID") ?? "";
const GMAIL_CLIENT_SECRET = Deno.env.get("GMAIL_CLIENT_SECRET") ?? "";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users";
const REQUEST_TIMEOUT_MS = 15_000;
const MAX_HISTORY_PAGES = 100;
const MAX_RECOVERY_PAGES = 100;
const MAX_BODY_BYTES = 256 * 1_024;
const MAX_ENCODED_BODY_LENGTH = Math.ceil(MAX_BODY_BYTES * 4 / 3) + 8;
const MAX_MESSAGE_JSON_BYTES = 8 * 1_024 * 1_024;
const MAX_MIME_PARTS = 200;

export class GoogleApiError extends Error {
  constructor(
    public readonly operation: string,
    public readonly status: number,
    public readonly code: string,
  ) {
    super(`${operation}_failed:${status}:${code}`);
    this.name = "GoogleApiError";
  }

  get retryable(): boolean {
    return this.status === 408 || this.status === 429 || this.status >= 500 ||
      ["rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "backendError", "internalError"].includes(
        this.code,
      );
  }
}

export class GmailHistoryExpiredError extends GoogleApiError {
  constructor() {
    super("history_list", 404, "history_expired");
    this.name = "GmailHistoryExpiredError";
  }
}

interface GmailMessagePartBody {
  data?: string;
  size?: number;
  attachmentId?: string;
}

export interface GmailMessagePart {
  mimeType?: string;
  filename?: string;
  headers?: Array<{ name?: string; value?: string }>;
  body?: GmailMessagePartBody;
  parts?: GmailMessagePart[];
}

export interface GmailMessage {
  id: string;
  headers: Map<string, string>;
  authenticationResults: string[];
  snippet: string;
  bodyText: string;
  internalDate: string | null;
  labelIds: string[];
}

export interface GmailWatchResult {
  historyId: string;
  expiration: string;
}

export interface GmailTokenSet {
  accessToken: string;
  refreshToken: string;
  grantedScopes: string[];
}

export interface GmailProfile {
  emailAddress: string;
  historyId: string;
}

export function gmailWatchExpirationIso(expiration: string, nowMs = Date.now()): string {
  const milliseconds = Number(expiration);
  if (!Number.isSafeInteger(milliseconds)) throw new Error("invalid_watch_expiration");
  const date = new Date(milliseconds);
  if (!Number.isFinite(date.getTime()) || date.getTime() <= nowMs) throw new Error("invalid_watch_expiration");
  return date.toISOString();
}

type FetchLike = typeof fetch;

function requireGoogleOAuthConfig(): void {
  if (!GMAIL_CLIENT_ID || !GMAIL_CLIENT_SECRET) throw new Error("google_oauth_not_configured");
}

async function safeErrorCode(response: Response): Promise<string> {
  try {
    const text = (await response.text()).slice(0, 16_384);
    const parsed = JSON.parse(text) as Record<string, unknown>;
    const error = parsed.error;
    if (typeof error === "string" && /^[a-zA-Z0-9_.-]{1,80}$/.test(error)) return error;
    if (error && typeof error === "object") {
      const record = error as Record<string, unknown>;
      const errors = record.errors;
      if (Array.isArray(errors)) {
        const reason = (errors[0] as Record<string, unknown> | undefined)?.reason;
        if (typeof reason === "string" && /^[a-zA-Z0-9_.-]{1,80}$/.test(reason)) return reason;
      }
      if (typeof record.status === "string" && /^[A-Z0-9_]{1,80}$/.test(record.status)) return record.status;
    }
  } catch {
    // Never include arbitrary provider response bodies in logs or thrown errors.
  }
  return "http_error";
}

async function checkedFetch(
  operation: string,
  input: string | URL,
  init: RequestInit,
  fetchImpl: FetchLike,
): Promise<Response> {
  let response: Response;
  try {
    response = await fetchImpl(input, { ...init, signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS) });
  } catch (error) {
    const code = error instanceof DOMException && error.name === "TimeoutError" ? "timeout" : "network_error";
    throw new GoogleApiError(operation, 503, code);
  }
  if (!response.ok) {
    if (operation === "history_list" && response.status === 404) throw new GmailHistoryExpiredError();
    throw new GoogleApiError(operation, response.status, await safeErrorCode(response));
  }
  return response;
}

async function checkedJson<T>(
  operation: string,
  input: string | URL,
  init: RequestInit,
  fetchImpl: FetchLike,
): Promise<T> {
  const response = await checkedFetch(operation, input, init, fetchImpl);
  try {
    return await response.json() as T;
  } catch {
    throw new GoogleApiError(operation, 502, "invalid_json");
  }
}

export async function exchangeAuthorizationCode(
  code: string,
  redirectUri: string,
  codeVerifier: string,
  fetchImpl: FetchLike = fetch,
): Promise<GmailTokenSet> {
  requireGoogleOAuthConfig();
  const json = await checkedJson<Record<string, unknown>>(
    "token_exchange",
    TOKEN_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code,
        client_id: GMAIL_CLIENT_ID,
        client_secret: GMAIL_CLIENT_SECRET,
        redirect_uri: redirectUri,
        grant_type: "authorization_code",
        code_verifier: codeVerifier,
      }),
    },
    fetchImpl,
  );
  if (typeof json.access_token !== "string" || typeof json.refresh_token !== "string") {
    throw new GoogleApiError("token_exchange", 502, "missing_tokens");
  }
  const scopes = typeof json.scope === "string" ? json.scope.split(/\s+/).filter(Boolean) : [];
  return { accessToken: json.access_token, refreshToken: json.refresh_token, grantedScopes: scopes };
}

export async function refreshAccessToken(refreshToken: string, fetchImpl: FetchLike = fetch): Promise<string> {
  requireGoogleOAuthConfig();
  if (!refreshToken) throw new Error("missing_refresh_token");
  const json = await checkedJson<Record<string, unknown>>(
    "token_refresh",
    TOKEN_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id: GMAIL_CLIENT_ID,
        client_secret: GMAIL_CLIENT_SECRET,
        refresh_token: refreshToken,
        grant_type: "refresh_token",
      }),
    },
    fetchImpl,
  );
  if (typeof json.access_token !== "string" || json.access_token.length < 16) {
    throw new GoogleApiError("token_refresh", 502, "missing_access_token");
  }
  return json.access_token;
}

export async function getGmailProfile(accessToken: string, fetchImpl: FetchLike = fetch): Promise<GmailProfile> {
  const json = await checkedJson<Record<string, unknown>>(
    "profile_get",
    `${GMAIL_API}/me/profile`,
    { headers: { Authorization: `Bearer ${accessToken}` } },
    fetchImpl,
  );
  if (
    typeof json.emailAddress !== "string" || !json.emailAddress.includes("@") ||
    typeof json.historyId !== "string" || !/^\d{1,64}$/.test(json.historyId)
  ) {
    throw new GoogleApiError("profile_get", 502, "invalid_response");
  }
  return {
    emailAddress: json.emailAddress.trim().toLowerCase(),
    historyId: json.historyId,
  };
}

export async function startWatch(
  accessToken: string,
  topicName: string,
  fetchImpl: FetchLike = fetch,
): Promise<GmailWatchResult> {
  if (!/^projects\/[a-z][a-z0-9:.-]{4,61}[a-z0-9]\/topics\/[A-Za-z][A-Za-z0-9._~+%-]{2,254}$/.test(topicName)) {
    throw new Error("invalid_pubsub_topic");
  }
  const json = await checkedJson<Record<string, unknown>>(
    "watch",
    `${GMAIL_API}/me/watch`,
    {
      method: "POST",
      headers: { Authorization: `Bearer ${accessToken}`, "Content-Type": "application/json" },
      body: JSON.stringify({
        topicName,
        labelIds: ["INBOX"],
        labelFilterBehavior: "include",
      }),
    },
    fetchImpl,
  );
  const historyId = String(json.historyId ?? "");
  const expiration = String(json.expiration ?? "");
  if (!/^\d{1,64}$/.test(historyId) || !/^\d{10,}$/.test(expiration)) {
    throw new GoogleApiError("watch", 502, "invalid_response");
  }
  return { historyId, expiration };
}

export async function listHistorySince(
  accessToken: string,
  startHistoryId: string,
  fetchImpl: FetchLike = fetch,
): Promise<{ newHistoryId: string; addedMessageIds: string[] }> {
  if (!/^\d{1,64}$/.test(startHistoryId)) throw new Error("invalid_history_id");
  const added = new Set<string>();
  let pageToken: string | undefined;
  let currentHistoryId = startHistoryId;

  for (let page = 0; page < MAX_HISTORY_PAGES; page++) {
    const url = new URL(`${GMAIL_API}/me/history`);
    url.searchParams.set("startHistoryId", startHistoryId);
    url.searchParams.set("historyTypes", "messageAdded");
    url.searchParams.set("labelId", "INBOX");
    url.searchParams.set("maxResults", "500");
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const json = await checkedJson<Record<string, unknown>>(
      "history_list",
      url,
      { headers: { Authorization: `Bearer ${accessToken}` } },
      fetchImpl,
    );
    if (typeof json.historyId !== "string" || !/^\d{1,64}$/.test(json.historyId)) {
      throw new GoogleApiError("history_list", 502, "invalid_response");
    }
    currentHistoryId = json.historyId;
    const history = Array.isArray(json.history) ? json.history : [];
    for (const rawEntry of history) {
      if (!rawEntry || typeof rawEntry !== "object") continue;
      const messagesAdded = (rawEntry as Record<string, unknown>).messagesAdded;
      if (!Array.isArray(messagesAdded)) continue;
      for (const rawAdded of messagesAdded) {
        const message = rawAdded && typeof rawAdded === "object" ? (rawAdded as Record<string, unknown>).message : null;
        const id = message && typeof message === "object" ? (message as Record<string, unknown>).id : null;
        if (typeof id === "string" && /^[A-Za-z0-9_-]{1,256}$/.test(id)) added.add(id);
      }
    }
    pageToken = typeof json.nextPageToken === "string" && json.nextPageToken ? json.nextPageToken : undefined;
    if (!pageToken) return { newHistoryId: currentHistoryId, addedMessageIds: [...added] };
  }
  throw new GoogleApiError("history_list", 503, "page_limit_exceeded");
}

/**
 * Full-sync fallback used only when Gmail expires a history cursor. It pages
 * every matching result; hitting the safety page limit throws so callers never
 * advance the cursor after a truncated recovery.
 */
export async function listRecentMessageIds(
  accessToken: string,
  lastSyncedAt: string | null = null,
  grantCreatedAt: string | null = null,
  fetchImpl: FetchLike = fetch,
): Promise<string[]> {
  const ids = new Set<string>();
  let pageToken: string | undefined;
  const recoveryBaseline = lastSyncedAt ?? grantCreatedAt;
  const baselineMs = recoveryBaseline ? Date.parse(recoveryBaseline) : Number.NaN;
  const timeQuery = Number.isFinite(baselineMs)
    ? `after:${Math.max(0, Math.floor(baselineMs / 1_000) - 300)}`
    : "newer_than:7d";
  const senderQuery =
    "{from:yape.pe from:bcplinkseguro.com from:viabcp.com from:interbank.com.pe from:bbva.com from:scotiabank.com.pe}";

  for (let page = 0; page < MAX_RECOVERY_PAGES; page++) {
    const url = new URL(`${GMAIL_API}/me/messages`);
    url.searchParams.set("maxResults", "500");
    url.searchParams.set("labelIds", "INBOX");
    url.searchParams.set("q", `${timeQuery} ${senderQuery}`);
    if (pageToken) url.searchParams.set("pageToken", pageToken);
    const json = await checkedJson<Record<string, unknown>>(
      "messages_list",
      url,
      { headers: { Authorization: `Bearer ${accessToken}` } },
      fetchImpl,
    );
    const messages = Array.isArray(json.messages) ? json.messages : [];
    for (const raw of messages) {
      const id = raw && typeof raw === "object" ? (raw as Record<string, unknown>).id : null;
      if (typeof id === "string" && /^[A-Za-z0-9_-]{1,256}$/.test(id)) ids.add(id);
    }
    pageToken = typeof json.nextPageToken === "string" && json.nextPageToken ? json.nextPageToken : undefined;
    if (!pageToken) return [...ids];
  }
  throw new GoogleApiError("messages_list", 503, "page_limit_exceeded");
}

export async function getMessage(
  accessToken: string,
  messageId: string,
  fetchImpl: FetchLike = fetch,
): Promise<GmailMessage | null> {
  if (!/^[A-Za-z0-9_-]{1,256}$/.test(messageId)) throw new Error("invalid_message_id");
  const url = new URL(`${GMAIL_API}/me/messages/${encodeURIComponent(messageId)}`);
  url.searchParams.set("format", "full");
  let response: Response;
  try {
    response = await fetchImpl(url, {
      headers: { Authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
  } catch (error) {
    const code = error instanceof DOMException && error.name === "TimeoutError" ? "timeout" : "network_error";
    throw new GoogleApiError("messages_get", 503, code);
  }
  if (response.status === 404) return null;
  if (!response.ok) throw new GoogleApiError("messages_get", response.status, await safeErrorCode(response));

  let json: Record<string, unknown>;
  try {
    const declaredLength = Number(response.headers.get("content-length") ?? "0");
    if (Number.isFinite(declaredLength) && declaredLength > MAX_MESSAGE_JSON_BYTES) {
      throw new GoogleApiError("messages_get", 413, "message_too_large");
    }
    const text = await response.text();
    if (new TextEncoder().encode(text).byteLength > MAX_MESSAGE_JSON_BYTES) {
      throw new GoogleApiError("messages_get", 413, "message_too_large");
    }
    json = JSON.parse(text) as Record<string, unknown>;
    if (!json || typeof json !== "object" || Array.isArray(json)) throw new Error("invalid_json");
  } catch (error) {
    if (error instanceof GoogleApiError) throw error;
    throw new GoogleApiError("messages_get", 502, "invalid_json");
  }
  const payload = json.payload && typeof json.payload === "object" ? json.payload as GmailMessagePart : {};
  const headers = new Map<string, string>();
  const authenticationResults: string[] = [];
  for (const header of payload.headers ?? []) {
    if (typeof header.name === "string" && typeof header.value === "string") {
      const name = header.name.toLowerCase();
      if (name === "authentication-results") authenticationResults.push(header.value);
      else headers.set(name, header.value);
    }
  }
  const snippet = decodeHtmlEntities(typeof json.snippet === "string" ? json.snippet : "");
  return {
    id: typeof json.id === "string" ? json.id : messageId,
    headers,
    authenticationResults,
    snippet,
    bodyText: extractMessageBody(payload) || snippet,
    internalDate: typeof json.internalDate === "string" ? json.internalDate : null,
    labelIds: Array.isArray(json.labelIds) ? json.labelIds.filter((id): id is string => typeof id === "string") : [],
  };
}

export function extractMessageBody(payload: GmailMessagePart): string {
  const plain: string[] = [];
  const html: string[] = [];
  let remaining = MAX_BODY_BYTES;
  let visitedParts = 0;

  const visit = (part: GmailMessagePart, depth: number): void => {
    if (depth > 20 || visitedParts >= MAX_MIME_PARTS || remaining <= 0) return;
    visitedParts++;
    const mimeType = (part.mimeType ?? "").toLowerCase().split(";", 1)[0]?.trim() ?? "";
    const filename = part.filename?.trim() ?? "";
    const data = part.body?.data;
    if (!filename && data && (mimeType === "text/plain" || mimeType === "text/html")) {
      const decoded = decodeBase64UrlText(data, remaining);
      remaining -= new TextEncoder().encode(decoded).byteLength;
      if (mimeType === "text/plain") plain.push(decoded);
      else html.push(decoded);
    }
    for (const child of part.parts ?? []) {
      if (visitedParts >= MAX_MIME_PARTS || remaining <= 0) break;
      visit(child, depth + 1);
    }
  };
  visit(payload, 0);

  const selected = plain.length > 0 ? plain.join("\n") : stripHtml(html.join("\n"));
  return normalizeBodyText(selected).slice(0, MAX_BODY_BYTES);
}

export function decodeBase64UrlText(encoded: string, maxBytes = MAX_BODY_BYTES): string {
  if (!encoded || encoded.length > Math.min(MAX_ENCODED_BODY_LENGTH, Math.ceil(maxBytes * 4 / 3) + 8)) return "";
  if (!/^[A-Za-z0-9_-]+={0,2}$/.test(encoded)) return "";
  try {
    const padded = encoded.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - encoded.length % 4) % 4);
    const binary = atob(padded);
    if (binary.length > maxBytes) return "";
    const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
    return new TextDecoder("utf-8").decode(bytes);
  } catch {
    return "";
  }
}

export function stripHtml(html: string): string {
  return decodeHtmlEntities(
    html
      .replace(/<(script|style|head|svg)[^>]*>[\s\S]*?<\/\1\s*>/gi, " ")
      .replace(/<br\s*\/?\s*>/gi, "\n")
      .replace(/<\/\s*(?:p|div|li|tr|h[1-6])\s*>/gi, "\n")
      .replace(/<[^>]{0,2000}>/g, " "),
  );
}

function normalizeBodyText(value: string): string {
  return value
    .replace(/\0/g, "")
    .replace(/[\t\f\v ]+/g, " ")
    .replace(/\s*\n\s*/g, "\n")
    .replace(/\n{3,}/g, "\n\n")
    .trim();
}

function decodeHtmlEntities(value: string): string {
  const named: Record<string, string> = {
    amp: "&",
    lt: "<",
    gt: ">",
    quot: '"',
    apos: "'",
    nbsp: " ",
  };
  return value.replace(/&(?:#(\d{1,7})|#x([0-9a-f]{1,6})|([a-z]{2,8}));/gi, (match, decimal, hex, name) => {
    if (name) return named[String(name).toLowerCase()] ?? match;
    const point = Number.parseInt(decimal ?? hex, decimal ? 10 : 16);
    return Number.isSafeInteger(point) && point > 0 && point <= 0x10ffff ? String.fromCodePoint(point) : match;
  });
}
