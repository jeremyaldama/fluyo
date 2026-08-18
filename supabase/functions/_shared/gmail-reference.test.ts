import { assertEquals, assertNotEquals, assertRejects } from "jsr:@std/assert@1";
import { gmailMailboxReferencePrefix } from "./gmail-reference.ts";

const SECRET = "test-only-dedupe-secret-that-is-at-least-32-bytes";

Deno.test("Gmail mailbox reference is canonical, stable and uses HMAC-SHA256", async () => {
  const expected = "gmail:v1:Arb1MnjWLVOOHbwO_ghWOfqCXobfeiPn20nd_nqedKw";
  assertEquals(await gmailMailboxReferencePrefix("user@example.com", SECRET), expected);
  assertEquals(await gmailMailboxReferencePrefix("  USER@EXAMPLE.COM ", SECRET), expected);
  assertEquals(expected.includes("user@example.com"), false);
});

Deno.test("Gmail mailbox reference differs across mailboxes", async () => {
  assertNotEquals(
    await gmailMailboxReferencePrefix("first@example.com", SECRET),
    await gmailMailboxReferencePrefix("second@example.com", SECRET),
  );
});

Deno.test("Gmail mailbox reference rejects a weak secret", async () => {
  await assertRejects(
    () => gmailMailboxReferencePrefix("user@example.com", "too-short"),
    Error,
    "GMAIL_DEDUPE_SECRET must be at least 32 bytes",
  );
});
