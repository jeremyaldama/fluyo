// Thin Gmail API client for the email-import flow.
//
// Responsibilities:
//   - exchange a stored refresh token for a fresh access token (OAuth)
//   - list message changes since a historyId cursor (gmail.users.history.list)
//   - fetch a single message's headers + snippet/body (gmail.users.messages.get)
//
// We call the REST API directly with fetch — no Google client library needed,
// which keeps the Edge Function bundle small.

const GMAIL_CLIENT_ID = Deno.env.get("GMAIL_CLIENT_ID") ?? "";
const GMAIL_CLIENT_SECRET = Deno.env.get("GMAIL_CLIENT_SECRET") ?? "";

const TOKEN_URL = "https://oauth2.googleapis.com/token";
const GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users";

export interface GmailMessageHeader {
  name: string;
  value: string;
}

export interface GmailMessage {
  id: string;
  headers: Map<string, string>; // lowercased header name → value
  snippet: string;
  /** Best-effort plaintext body (HTML stripped) for amount extraction. */
  bodyText: string;
  internalDate: string | null; // epoch ms as string
}

/**
 * Exchange a stored refresh token for a fresh access token. Returns the access
 * token (1h lifetime) — we never persist it; the next webhook refreshes again.
 */
export async function refreshAccessToken(refreshToken: string): Promise<string> {
  const body = new URLSearchParams({
    client_id: GMAIL_CLIENT_ID,
    client_secret: GMAIL_CLIENT_SECRET,
    refresh_token: refreshToken,
    grant_type: "refresh_token",
  });
  const res = await fetch(TOKEN_URL, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body,
  });
  if (!res.ok) {
    throw new Error(`token refresh failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  return json.access_token as string;
}

/**
 * List message changes since the given historyId. Returns the new historyId
 * cursor and the list of added message ids. Gmail's history.list is the right
 * call for push: the Pub/Sub payload only gives us a historyId, not message ids.
 *
 * Ref: https://developers.google.com/gmail/api/reference/rest/v1/users.history/list
 */
export async function listHistorySince(
  accessToken: string,
  startHistoryId: string,
): Promise<{ newHistoryId: string; addedMessageIds: string[] }> {
  const url = `${GMAIL_API}/me/history?startHistoryId=${encodeURIComponent(startHistoryId)}&historyTypes=messageAdded&maxResults=50`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) {
    throw new Error(`history.list failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  const added: string[] = [];
  for (const h of json.history ?? []) {
    for (const m of h.messagesAdded ?? []) {
      if (m.message?.id) added.push(m.message.id);
    }
  }
  return {
    newHistoryId: json.historyId ?? startHistoryId,
    addedMessageIds: added,
  };
}

/** If the grant has no cursor yet, use messages.list to seed it from recent mail. */
export async function listRecentMessageIds(accessToken: string, max = 5): Promise<{ historyId: string; messageIds: string[] }> {
  const url = `${GMAIL_API}/me/messages?maxResults=${max}&q=newer_than:1d`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) {
    throw new Error(`messages.list failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  return {
    historyId: String(json.historyId ?? ""),
    messageIds: (json.messages ?? []).map((m: { id: string }) => m.id),
  };
}

/**
 * Fetch a single message with headers + a best-effort plaintext body.
 * format=metadata gives us From/Subject/Date headers; we then fetch the body
 * snippet (truncated, but enough for amount extraction in most notifications).
 */
export async function getMessage(accessToken: string, messageId: string): Promise<GmailMessage | null> {
  // metadata format: headers + snippet, no raw body. Cheapest call that's
  // enough for most receipt notifications (amount is usually in subject/snippet).
  const url = `${GMAIL_API}/me/messages/${messageId}?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date`;
  const res = await fetch(url, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (res.status === 404) return null; // message deleted between history and get
  if (!res.ok) {
    throw new Error(`messages.get failed: ${res.status} ${await res.text()}`);
  }
  const json = await res.json();
  const headers = new Map<string, string>();
  for (const h of json.payload?.headers ?? []) {
    headers.set(String(h.name).toLowerCase(), String(h.value));
  }
  return {
    id: json.id,
    headers,
    snippet: decodeHtmlEntities(json.snippet ?? ""),
    bodyText: decodeHtmlEntities(json.snippet ?? ""), // snippet is plaintext-ish
    internalDate: json.internalDate ?? null,
  };
}

/** Minimal HTML-entity decode for the snippet (Gmail returns some entities). */
function decodeHtmlEntities(s: string): string {
  return s
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'");
}
