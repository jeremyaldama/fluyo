import {
  advanceGrantCursor,
  bearerToken,
  type EmailGrantRow,
  getGrantByEmail,
  type GrantErrorCode,
  insertEmailExpenseIfGrantActive,
  markGrantError,
  SupabaseServiceError,
  updateGrantWatch,
} from "../_shared/db.ts";
import { expenseDateForReceipt } from "../_shared/expense-date.ts";
import {
  getMessage,
  GmailHistoryExpiredError,
  gmailWatchExpirationIso,
  GoogleApiError,
  listHistorySince,
  listRecentMessageIds,
  refreshAccessToken,
  startWatch,
} from "../_shared/gmail-client.ts";
import { verifyGoogleOidcJwt } from "../_shared/google-oidc.ts";
import { plainTextResponse as textResponse } from "../_shared/http-response.ts";
import { decodeGmailPubSubData, type GmailNotification } from "../_shared/pubsub-notification.ts";
import { parseReceipt } from "../_shared/receipt-parser.ts";

declare const EdgeRuntime: { waitUntil<T>(promise: Promise<T>): void };

const PUBSUB_TOPIC = Deno.env.get("GOOGLE_PUBSUB_TOPIC") ?? "";
const PUSH_AUDIENCE = Deno.env.get("GOOGLE_PUBSUB_PUSH_AUDIENCE") ?? "";
const PUSH_SERVICE_ACCOUNT = Deno.env.get("GOOGLE_PUBSUB_PUSH_SERVICE_ACCOUNT_EMAIL") ?? "";
const EXPECTED_SUBSCRIPTION = Deno.env.get("GOOGLE_PUBSUB_SUBSCRIPTION") ?? "";
const MAX_REQUEST_BODY_BYTES = 1_048_576;
const MESSAGE_CONCURRENCY = 5;

interface PubSubEnvelope {
  message?: {
    data?: string;
    messageId?: string;
    publishTime?: string;
  };
  subscription?: string;
}

class PermanentNotificationError extends Error {}
class PermanentProcessingError extends Error {}
class InactiveGrantError extends Error {}

export function decodeNotification(payload: PubSubEnvelope): GmailNotification | null {
  const data = payload.message?.data;
  return data ? decodeGmailPubSubData(data) : null;
}

async function readEnvelope(req: Request): Promise<PubSubEnvelope> {
  const contentLength = Number(req.headers.get("content-length") ?? "0");
  if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BODY_BYTES) {
    throw new PermanentNotificationError("body_too_large");
  }
  const text = await req.text();
  if (text.length > MAX_REQUEST_BODY_BYTES) throw new PermanentNotificationError("body_too_large");
  let payload: unknown;
  try {
    payload = JSON.parse(text);
  } catch {
    throw new PermanentNotificationError("invalid_json");
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) {
    throw new PermanentNotificationError("invalid_envelope");
  }
  return payload as PubSubEnvelope;
}

async function authenticatePush(req: Request): Promise<void> {
  if (!PUSH_AUDIENCE || !PUSH_SERVICE_ACCOUNT || !EXPECTED_SUBSCRIPTION || !PUBSUB_TOPIC) {
    throw new Error("pubsub_auth_not_configured");
  }
  const token = bearerToken(req);
  if (!token) throw new PermanentNotificationError("missing_oidc_token");
  await verifyGoogleOidcJwt(token, {
    audience: PUSH_AUDIENCE,
    serviceAccountEmail: PUSH_SERVICE_ACCOUNT,
  });
}

async function safeMarkError(grantId: string, code: GrantErrorCode): Promise<void> {
  try {
    await markGrantError(grantId, code);
  } catch {
    // Preserve the original failure so Pub/Sub retry semantics remain correct.
  }
}

function cleanHeader(value: string, maxLength: number): string | null {
  const clean = value.replace(/[\r\n\0]+/g, " ").replace(/\s+/g, " ").trim().slice(0, maxLength);
  return clean || null;
}

async function processMessage(
  grant: EmailGrantRow,
  accessToken: string,
  messageId: string,
): Promise<"inserted" | "duplicate" | "ignored"> {
  let message: Awaited<ReturnType<typeof getMessage>>;
  try {
    message = await getMessage(accessToken, messageId);
  } catch (error) {
    // A malformed/oversized individual message is poison data, not a reason to
    // replay every other message in the history page forever.
    if (error instanceof GoogleApiError && (error.status === 400 || error.status === 413)) return "ignored";
    throw error;
  }
  if (!message || !message.labelIds.includes("INBOX")) return "ignored";
  const subject = message.headers.get("subject") ?? "";
  const parsed = parseReceipt({
    from: message.headers.get("from") ?? "",
    subject,
    body: message.bodyText,
    authenticationResults: message.authenticationResults,
  });
  if (!parsed) return "ignored";

  const internalDate = message.internalDate && /^\d{1,16}$/.test(message.internalDate)
    ? Number(message.internalDate)
    : Number.NaN;
  try {
    const status = await insertEmailExpenseIfGrantActive({
      grant_id: grant.grant_id,
      message_id: messageId,
      amount: parsed.amount,
      description: cleanHeader(subject, 240),
      expense_date: expenseDateForReceipt(parsed.expenseDate, internalDate),
      recipient: cleanHeader(parsed.recipient ?? "", 120),
    });
    if (status === "inactive") throw new InactiveGrantError("inactive_grant");
    return status;
  } catch (error) {
    // Invalid data in one receipt is poison input. Do not replay the whole
    // history batch forever, but never swallow database availability failures.
    if (error instanceof SupabaseServiceError && error.code === "22023") {
      console.warn("gmail-webhook ignored invalid expense data");
      return "ignored";
    }
    throw error;
  }
}

async function processMessages(
  grant: EmailGrantRow,
  accessToken: string,
  messageIds: string[],
): Promise<{ inserted: number; duplicates: number; ignored: number }> {
  if (messageIds.length === 0) return { inserted: 0, duplicates: 0, ignored: 0 };
  const counts = { inserted: 0, duplicates: 0, ignored: 0 };
  for (let offset = 0; offset < messageIds.length; offset += MESSAGE_CONCURRENCY) {
    const results = await Promise.all(
      messageIds.slice(offset, offset + MESSAGE_CONCURRENCY)
        .map((messageId) => processMessage(grant, accessToken, messageId)),
    );
    for (const result of results) {
      if (result === "duplicate") counts.duplicates++;
      else counts[result]++;
    }
  }
  return counts;
}

async function processNotification(notification: GmailNotification): Promise<void> {
  const grant = await getGrantByEmail(notification.emailAddress);
  if (!grant) return;
  if (!grant.mailbox_fingerprint) {
    // A pre-migration row cannot build the stable private idempotency key. It
    // needs relinking; do not process messages or advance its cursor silently.
    await markGrantError(grant.grant_id, "relink_required");
    return;
  }
  if (!grant.refresh_token) {
    await markGrantError(grant.grant_id, "token_refresh_failed");
    return;
  }

  let accessToken: string;
  try {
    accessToken = await refreshAccessToken(grant.refresh_token);
  } catch (error) {
    if (error instanceof GoogleApiError && !error.retryable) {
      // Persist the actionable state before ACKing a permanently invalid grant.
      await markGrantError(grant.grant_id, "token_refresh_failed");
      return;
    }
    await safeMarkError(grant.grant_id, "token_refresh_failed");
    throw error;
  }

  try {
    let messageIds: string[];
    let newCursor: string;
    if (grant.history_id) {
      try {
        const history = await listHistorySince(accessToken, grant.history_id);
        messageIds = history.addedMessageIds;
        newCursor = history.newHistoryId;
      } catch (error) {
        if (!(error instanceof GmailHistoryExpiredError)) throw error;
        const watch = await startWatch(accessToken, PUBSUB_TOPIC);
        await updateGrantWatch(grant.grant_id, gmailWatchExpirationIso(watch.expiration));
        messageIds = await listRecentMessageIds(
          accessToken,
          grant.last_synced_at ?? null,
          grant.created_at ?? null,
        );
        newCursor = watch.historyId;
      }
    } else {
      const watch = await startWatch(accessToken, PUBSUB_TOPIC);
      await updateGrantWatch(grant.grant_id, gmailWatchExpirationIso(watch.expiration));
      messageIds = await listRecentMessageIds(
        accessToken,
        grant.last_synced_at ?? null,
        grant.created_at ?? null,
      );
      newCursor = watch.historyId;
    }

    const counts = await processMessages(grant, accessToken, messageIds);
    await advanceGrantCursor(grant.grant_id, newCursor);
    console.log(
      `gmail-webhook complete inserted=${counts.inserted} duplicate=${counts.duplicates} ignored=${counts.ignored}`,
    );
  } catch (error) {
    // The grant was disconnected/replaced after this notification started.
    // Its old work is no longer authorized and must not affect the new cursor.
    if (error instanceof InactiveGrantError) throw error;
    const code: GrantErrorCode = error instanceof GoogleApiError
      ? (error.operation === "watch" ? "watch_failed" : "gmail_api_failed")
      : "webhook_failed";
    if (error instanceof GoogleApiError && !error.retryable) {
      await markGrantError(grant.grant_id, code);
      throw new PermanentProcessingError(code);
    }
    await safeMarkError(grant.grant_id, code);
    throw error;
  }
}

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") return textResponse("method not allowed", 405);
  try {
    await authenticatePush(req);
  } catch (error) {
    if (error instanceof PermanentNotificationError) return textResponse("unauthorized", 401);
    console.error("gmail-webhook OIDC verification failed");
    return textResponse(
      error instanceof Error && error.message === "pubsub_auth_not_configured" ? "unavailable" : "unauthorized",
      error instanceof Error && error.message === "pubsub_auth_not_configured" ? 503 : 401,
    );
  }

  let envelope: PubSubEnvelope;
  try {
    envelope = await readEnvelope(req);
  } catch {
    // Authenticated but malformed messages cannot succeed on retry; ACK/drop.
    console.warn("gmail-webhook dropped malformed envelope");
    return textResponse("", 204);
  }
  if (envelope.subscription !== EXPECTED_SUBSCRIPTION) return textResponse("forbidden", 403);
  if (
    typeof envelope.message?.messageId !== "string" ||
    !/^[A-Za-z0-9_-]{1,256}$/.test(envelope.message.messageId)
  ) return textResponse("", 204);
  const notification = decodeNotification(envelope);
  if (!notification) return textResponse("", 204);

  // Register with the Edge runtime, but await the same promise so a transient
  // failure returns 5xx and Pub/Sub redelivers. Idempotent inserts make retries safe.
  const task = processNotification(notification);
  EdgeRuntime.waitUntil(task.catch(() => undefined));
  try {
    await task;
    return textResponse("", 204);
  } catch (error) {
    if (error instanceof InactiveGrantError) return textResponse("", 204);
    if (error instanceof PermanentProcessingError) {
      console.warn(`gmail-webhook permanent failure:${error.message}`);
      return textResponse("", 204);
    }
    console.error(`gmail-webhook processing failed:${safeError(error)}`);
    return textResponse("retry", 503);
  }
});

function safeError(error: unknown): string {
  if (error instanceof GoogleApiError) return `${error.operation}:${error.status}:${error.code}`;
  return error instanceof Error && /^[a-zA-Z0-9_.:-]{1,128}$/.test(error.message) ? error.message : "unknown_error";
}
