import { assert, assertEquals } from "jsr:@std/assert@1";
import { decodeGmailPubSubData } from "./pubsub-notification.ts";

function standardBase64(value: unknown): string {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

Deno.test("Pub/Sub notification accepts canonical standard Base64 with +, / and padding", () => {
  const data = standardBase64({
    emailAddress: "User@example.com",
    historyId: "123",
    proof: "\u0080\u03ff",
  });
  assert(data.includes("+"));
  assert(data.includes("/"));
  assert(data.endsWith("="));

  assertEquals(decodeGmailPubSubData(data), {
    emailAddress: "user@example.com",
    historyId: "123",
  });
});

Deno.test("Pub/Sub notification rejects Base64 and Base64url alphabet mixtures", () => {
  const standard = standardBase64({
    emailAddress: "user@example.com",
    historyId: "123",
    proof: "\u0080\u03ff",
  });
  assert(standard.includes("+"));
  assert(standard.includes("/"));

  assertEquals(decodeGmailPubSubData(standard.replace("+", "-")), null);
  assertEquals(decodeGmailPubSubData(standard.replace("/", "_")), null);
});

Deno.test("Pub/Sub notification rejects malformed padding and length", () => {
  const valid = standardBase64({ emailAddress: "user@example.com", historyId: "123" });
  assert(valid.endsWith("="));

  assertEquals(decodeGmailPubSubData(`${valid}=`), null);
  assertEquals(decodeGmailPubSubData(valid.slice(0, -1)), null);
  assertEquals(decodeGmailPubSubData(`=${valid.slice(1)}`), null);
});

Deno.test("Pub/Sub notification rejects non-canonical padding bits", () => {
  // Both strings decode permissively to {"a":1}, but only ...Q== has zeroed
  // unused padding bits. The decoder must reject the alternate representation.
  assertEquals(atob("eyJhIjoxfQ=="), atob("eyJhIjoxfR=="));
  assertEquals(decodeGmailPubSubData("eyJhIjoxfR=="), null);
});

Deno.test("Pub/Sub notification preserves fatal UTF-8 and JSON validation", () => {
  assertEquals(decodeGmailPubSubData("/w=="), null);
  assertEquals(decodeGmailPubSubData(standardBase64("not an object")), null);
  assertEquals(decodeGmailPubSubData(standardBase64({ emailAddress: "user@example.com" })), null);
});

Deno.test("Pub/Sub notification bounds encoded data before decoding", () => {
  assertEquals(decodeGmailPubSubData("A".repeat(8_193)), null);
});
