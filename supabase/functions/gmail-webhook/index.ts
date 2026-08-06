// gmail-webhook — receiver for Gmail Pub/Sub push notifications.
//
// Flow: Gmail sees a mailbox change → publishes to our Pub/Sub topic → Pub/Sub
// POSTs here. The payload is NOT the email; it's only:
//   { message: { data: base64url({emailAddress, historyId}), messageId, publishTime } }
// So this function must: decode the payload, look up the grant, refresh the
// user's access token, fetch the new messages via history.list, parse each for
// a receipt, and insert any expenses.
//
// We ACK the webhook (200) immediately and do the heavy lifting in a background
// task via EdgeRuntime.wait(), so Pub/Sub doesn't retry while we work. Errors
// are logged but swallowed (a single failed sync shouldn't break delivery).

import { getGrantByEmail, updateGrantHistoryId, insertExpense, getDecryptedVaultSecret } from "../_shared/db.ts";
import { refreshAccessToken, listHistorySince, listRecentMessageIds, getMessage } from "../_shared/gmail-client.ts";
import { parseReceipt } from "../_shared/receipt-parser.ts";

interface PubSubMessage {
  message?: {
    data?: string; // base64url-encoded JSON
    messageId?: string;
    publishTime?: string;
  };
  subscription?: string;
}

interface GmailNotification {
  emailAddress: string;
  historyId: string;
}

/** Decode the Pub/Sub payload. Returns null if malformed. */
function decodeNotification(payload: PubSubMessage): GmailNotification | null {
  const data = payload.message?.data;
  if (!data) return null;
  try {
    // base64url → base64 → JSON
    const b64 = data.replace(/-/g, "+").replace(/_/g, "/");
    const json = atob(b64);
    const parsed = JSON.parse(json);
    if (parsed.emailAddress && parsed.historyId) return parsed;
    return null;
  } catch {
    return null;
  }
}

Deno.serve(async (req: Request) => {
  // GET with ?my_param is Google's ownership verification challenge for the
  // push subscription endpoint. Respond 200 to confirm we own it.
  if (req.method === "GET") {
    return new Response("ok", { status: 200 });
  }

  let payload: PubSubMessage;
  try {
    payload = await req.json();
  } catch {
    return new Response("bad json", { status: 400 });
  }

  const notification = decodeNotification(payload);
  if (!notification) {
    return new Response("ok", { status: 200 }); // ACK malformed, don't retry
  }

  // ACK immediately; process in the background so Pub/Sub doesn't time out.
  EdgeRuntime.wait(processNotification(notification));
  return new Response("ok", { status: 200 });
});

/**
 * Background processing: fetch new messages, parse each for a receipt, insert
 * expenses. Every step is guarded so one user's bad message never affects
 * another or crashes the worker.
 */
async function processNotification({ emailAddress, historyId }: GmailNotification): Promise<void> {
  try {
    const grant = await getGrantByEmail(emailAddress);
    if (!grant || !grant.google_refresh_token_secret_id) {
      // User hasn't linked this mailbox (or revoked). Drop silently — we never
      // process mail for an unlinked account.
      return;
    }

    const refreshToken = await getDecryptedVaultSecret(grant.google_refresh_token_secret_id);
    const accessToken = await refreshAccessToken(refreshToken);

    // Determine which messages are new since our cursor. If we have no cursor
    // yet (first webhook after linking), seed from recent mail instead.
    let messageIds: string[];
    let newCursor: string;
    if (grant.history_id) {
      const hist = await listHistorySince(accessToken, grant.history_id);
      messageIds = hist.addedMessageIds;
      newCursor = hist.newHistoryId;
    } else {
      const recent = await listRecentMessageIds(accessToken);
      messageIds = recent.messageIds;
      newCursor = recent.historyId || historyId;
    }

    // Process each message. We deliberately don't batch-insert: a failure in
    // one message must not block the others.
    let inserted = 0;
    for (const id of messageIds) {
      try {
        const msg = await getMessage(accessToken, id);
        if (!msg) continue;

        const from = msg.headers.get("from") ?? "";
        const subject = msg.headers.get("subject") ?? "";
        const parsed = parseReceipt({ from, subject, body: msg.bodyText });
        if (!parsed) continue; // not a recognized receipt → skip

        await insertExpense({
          user_id: grant.user_id,
          amount: parsed.amount,
          description: subject || null,
          expense_date: parsed.expenseDate,
          source: "email",
          recipient: parsed.recipient,
        });
        inserted++;
      } catch (err) {
        // Log and continue; one bad message shouldn't abort the batch.
        console.error(`message ${id} failed:`, err instanceof Error ? err.message : err);
      }
    }

    // Advance the cursor only after we've attempted all messages. If we crashed
    // mid-batch the cursor stays put and the retry reprocesses (idempotency is
    // the user's responsibility for now — TODO: dedupe by message id in v2).
    await updateGrantHistoryId(grant.id, newCursor);
    console.log(`gmail-webhook: ${emailAddress} inserted ${inserted} expense(s)`);
  } catch (err) {
    // Top-level error: log but don't throw (we already ACK'd the webhook).
    console.error(`gmail-webhook error for ${emailAddress}:`, err instanceof Error ? err.message : err);
  }
}
