import {
  bearerToken,
  type EmailGrantRow,
  listGrantsDueForRenewal,
  markGrantError,
  updateGrantWatch,
} from "../_shared/db.ts";
import { gmailWatchExpirationIso, GoogleApiError, refreshAccessToken, startWatch } from "../_shared/gmail-client.ts";

const CRON_SECRET = Deno.env.get("GMAIL_CRON_SECRET") ?? "";
const PUBSUB_TOPIC = Deno.env.get("GOOGLE_PUBSUB_TOPIC") ?? "";
const GMAIL_CLIENT_ID = Deno.env.get("GMAIL_CLIENT_ID") ?? "";
const GMAIL_CLIENT_SECRET = Deno.env.get("GMAIL_CLIENT_SECRET") ?? "";
const RENEW_BEFORE_MS = 6 * 24 * 60 * 60 * 1_000;
const CONCURRENCY = 5;

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Cache-Control": "no-store",
      "Content-Type": "application/json; charset=utf-8",
      "X-Content-Type-Options": "nosniff",
    },
  });
}

function constantTimeEqual(left: string, right: string): boolean {
  const encoder = new TextEncoder();
  const a = encoder.encode(left);
  const b = encoder.encode(right);
  let difference = a.length ^ b.length;
  const length = Math.max(a.length, b.length);
  for (let index = 0; index < length; index++) {
    difference |= (a[index] ?? 0) ^ (b[index] ?? 0);
  }
  return difference === 0;
}

async function renewGrant(grant: EmailGrantRow): Promise<boolean> {
  if (!grant.mailbox_fingerprint) {
    await markGrantError(grant.grant_id, "relink_required");
    return false;
  }
  if (!grant.refresh_token) {
    await markGrantError(grant.grant_id, "token_refresh_failed");
    return false;
  }
  let accessToken: string;
  try {
    accessToken = await refreshAccessToken(grant.refresh_token);
  } catch (error) {
    if (error instanceof GoogleApiError && error.retryable) return false;
    if (!(error instanceof GoogleApiError)) throw error;
    await markGrantError(grant.grant_id, "token_refresh_failed");
    return false;
  }

  try {
    const watch = await startWatch(accessToken, PUBSUB_TOPIC);
    await updateGrantWatch(
      grant.grant_id,
      gmailWatchExpirationIso(watch.expiration),
      watch.historyId,
    );
    return true;
  } catch {
    try {
      await markGrantError(grant.grant_id, "watch_failed");
    } catch {
      // The summary still reports failure; the next daily run retries it.
    }
    return false;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return jsonResponse({ error: "method_not_allowed" }, 405);
  if (
    !CRON_SECRET || CRON_SECRET.length < 32 || !PUBSUB_TOPIC ||
    !GMAIL_CLIENT_ID || !GMAIL_CLIENT_SECRET
  ) {
    return jsonResponse({ error: "server_not_configured" }, 503);
  }
  const provided = bearerToken(req) ?? "";
  if (!constantTimeEqual(provided, CRON_SECRET)) return jsonResponse({ error: "unauthorized" }, 401);

  try {
    const before = new Date(Date.now() + RENEW_BEFORE_MS).toISOString();
    const grants = await listGrantsDueForRenewal(before);
    let renewed = 0;
    let failed = 0;
    for (let offset = 0; offset < grants.length; offset += CONCURRENCY) {
      const results = await Promise.all(grants.slice(offset, offset + CONCURRENCY).map(renewGrant));
      for (const success of results) success ? renewed++ : failed++;
    }
    console.log(`gmail-renew complete examined=${grants.length} renewed=${renewed} failed=${failed}`);
    return jsonResponse({ examined: grants.length, renewed, failed });
  } catch {
    console.error("gmail-renew failed");
    return jsonResponse({ error: "renewal_failed" }, 503);
  }
});
