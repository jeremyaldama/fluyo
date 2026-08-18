import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import {
  extractMessageBody,
  getGmailProfile,
  getMessage,
  type GmailMessagePart,
  GoogleApiError,
  listHistorySince,
  listRecentMessageIds,
  startWatch,
  stripHtml,
} from "./gmail-client.ts";

function b64url(value: string): string {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

Deno.test("extractMessageBody prefers text/plain in nested MIME", () => {
  const body = extractMessageBody({
    mimeType: "multipart/alternative",
    parts: [
      { mimeType: "text/html", body: { data: b64url("<b>S/ 99</b>") } },
      { mimeType: "text/plain", body: { data: b64url("Pago S/ 12.50") } },
    ],
  });
  assertEquals(body, "Pago S/ 12.50");
});

Deno.test("extractMessageBody strips HTML when plain text is absent", () => {
  const body = extractMessageBody({
    mimeType: "text/html",
    body: { data: b64url("<style>.x{}</style><p>Pago&nbsp;S/ 20</p><script>secret()</script>") },
  });
  assertEquals(body, "Pago S/ 20");
  assertEquals(stripHtml("a<br>b"), "a\nb");
});

Deno.test("extractMessageBody bounds adversarial MIME breadth", () => {
  const parts: GmailMessagePart[] = Array.from(
    { length: 200 },
    () => ({ mimeType: "application/octet-stream" }),
  );
  parts.push({ mimeType: "text/plain", body: { data: b64url("must not be reached") } });
  assertEquals(extractMessageBody({ mimeType: "multipart/mixed", parts }), "");
});

Deno.test("getMessage keeps duplicate Authentication-Results in message order", async () => {
  const mockFetch = (() =>
    Promise.resolve(Response.json({
      id: "m1",
      labelIds: ["INBOX"],
      payload: {
        mimeType: "text/plain",
        headers: [
          { name: "Authentication-Results", value: "mx.google.com; dmarc=pass header.from=yape.pe" },
          { name: "From", value: "Yape <yape@yape.pe>" },
          { name: "Authentication-Results", value: "attacker.example; dmarc=pass header.from=yape.pe" },
        ],
        body: { data: b64url("Yapeaste a Ana por S/ 10") },
      },
    }))) as typeof fetch;
  const message = await getMessage("access", "m1", mockFetch);
  assertEquals(message?.authenticationResults, [
    "mx.google.com; dmarc=pass header.from=yape.pe",
    "attacker.example; dmarc=pass header.from=yape.pe",
  ]);
});

Deno.test("getMessage rejects an oversized JSON response before parsing", async () => {
  const mockFetch = (() =>
    Promise.resolve(
      new Response("{}", {
        headers: { "content-length": String(9 * 1_024 * 1_024) },
      }),
    )) as typeof fetch;
  await assertRejects(() => getMessage("access", "m1", mockFetch), Error, "messages_get_failed:413:message_too_large");
});

Deno.test("listHistorySince paginates and deduplicates message ids", async () => {
  const requested: URL[] = [];
  const mockFetch = ((input: string | URL) => {
    const url = new URL(input);
    requested.push(url);
    const page = url.searchParams.get("pageToken");
    return Promise.resolve(
      Response.json(
        page
          ? { historyId: "30", history: [{ messagesAdded: [{ message: { id: "m2" } }, { message: { id: "m3" } }] }] }
          : {
            historyId: "20",
            nextPageToken: "next",
            history: [{ messagesAdded: [{ message: { id: "m1" } }, { message: { id: "m2" } }] }],
          },
      ),
    );
  }) as typeof fetch;

  const result = await listHistorySince("access", "10", mockFetch);
  assertEquals(result, { newHistoryId: "30", addedMessageIds: ["m1", "m2", "m3"] });
  assertEquals(requested.length, 2);
  assertEquals(requested[0]!.searchParams.get("maxResults"), "500");
  assertEquals(requested[1]!.searchParams.get("pageToken"), "next");
});

Deno.test("Gmail history responses reject ids outside the DB contract", async () => {
  const invalidHistoryFetch =
    (() => Promise.resolve(Response.json({ historyId: "1".repeat(65), history: [] }))) as typeof fetch;
  await assertRejects(
    () => listHistorySince("access", "10", invalidHistoryFetch),
    GoogleApiError,
    "history_list_failed:502:invalid_response",
  );

  const invalidWatchFetch =
    (() => Promise.resolve(Response.json({ historyId: "1".repeat(65), expiration: "1800000000000" }))) as typeof fetch;
  await assertRejects(
    () => startWatch("access", "projects/fluyo-prod/topics/gmail-receipts", invalidWatchFetch),
    GoogleApiError,
    "watch_failed:502:invalid_response",
  );
});

Deno.test("getGmailProfile returns the durable pre-watch history baseline", async () => {
  const mockFetch =
    (() =>
      Promise.resolve(Response.json({ emailAddress: " Student@Gmail.com ", historyId: "987654321" }))) as typeof fetch;
  assertEquals(await getGmailProfile("access", mockFetch), {
    emailAddress: "student@gmail.com",
    historyId: "987654321",
  });
});

Deno.test("getGmailProfile rejects a missing or out-of-contract history baseline", async () => {
  for (const historyId of [undefined, "", "not-numeric", "1".repeat(65)]) {
    const mockFetch = (() =>
      Promise.resolve(Response.json({ emailAddress: "student@gmail.com", historyId }))) as typeof fetch;
    await assertRejects(
      () => getGmailProfile("access", mockFetch),
      GoogleApiError,
      "profile_get_failed:502:invalid_response",
    );
  }
});

Deno.test("recovery listing paginates fully from last sync with overlap", async () => {
  const requested: URL[] = [];
  const mockFetch = ((input: string | URL) => {
    const url = new URL(input);
    requested.push(url);
    return Promise.resolve(
      Response.json(
        url.searchParams.has("pageToken")
          ? { messages: [{ id: "m2" }, { id: "m3" }] }
          : { messages: [{ id: "m1" }, { id: "m2" }], nextPageToken: "next" },
      ),
    );
  }) as typeof fetch;
  const ids = await listRecentMessageIds("access", "2023-11-14T22:13:20.000Z", null, mockFetch);
  assertEquals(ids, ["m1", "m2", "m3"]);
  assertEquals(requested.length, 2);
  assertEquals(requested[0]!.searchParams.get("q")?.includes("after:1699999700"), true);
  assertEquals(requested[0]!.searchParams.get("labelIds"), "INBOX");
});

Deno.test("recovery uses grant creation when no sync has ever completed", async () => {
  let requested: URL | null = null;
  const mockFetch = ((input: string | URL) => {
    requested = new URL(input);
    return Promise.resolve(Response.json({ messages: [] }));
  }) as typeof fetch;

  await listRecentMessageIds("access", null, "2023-11-14T22:13:20.000Z", mockFetch);
  assertEquals(requested!.searchParams.get("q")?.includes("after:1699999700"), true);
});

Deno.test("startWatch sends Gmail's lowercase include enum", async () => {
  let requestBody: Record<string, unknown> = {};
  const mockFetch = ((_input: string | URL, init?: RequestInit) => {
    requestBody = JSON.parse(String(init?.body));
    return Promise.resolve(Response.json({ historyId: "123", expiration: "1800000000000" }));
  }) as typeof fetch;

  await startWatch("access", "projects/fluyo-prod/topics/gmail-receipts", mockFetch);
  assertEquals(requestBody, {
    topicName: "projects/fluyo-prod/topics/gmail-receipts",
    labelIds: ["INBOX"],
    labelFilterBehavior: "include",
  });
});

Deno.test("Google API retryability includes Gmail 403 rate limits", () => {
  assertEquals(new GoogleApiError("messages_get", 403, "rateLimitExceeded").retryable, true);
  assertEquals(new GoogleApiError("messages_get", 403, "userRateLimitExceeded").retryable, true);
  assertEquals(new GoogleApiError("messages_get", 403, "PERMISSION_DENIED").retryable, false);
  assertEquals(new GoogleApiError("messages_get", 500, "backendError").retryable, true);
  assertEquals(new GoogleApiError("messages_get", 403, "internalError").retryable, true);
});
