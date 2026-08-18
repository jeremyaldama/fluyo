import { assertEquals } from "jsr:@std/assert@1";
import { expenseDateForReceipt } from "./expense-date.ts";

const NOW = Date.parse("2026-08-17T12:00:00Z");

Deno.test("expense date prefers a plausible parsed receipt date", () => {
  assertEquals(expenseDateForReceipt("2026-08-16", Number.NaN, NOW), "2026-08-16");
});

Deno.test("expense date falls back to Gmail internalDate in Lima", () => {
  const justAfterUtcMidnight = Date.parse("2026-08-17T02:00:00Z");
  assertEquals(expenseDateForReceipt(null, justAfterUtcMidnight, NOW), "2026-08-16");
});

Deno.test("expense date rejects out-of-range mail data and always stays non-null", () => {
  assertEquals(expenseDateForReceipt("2099-01-01", Date.parse("1999-01-01T00:00:00Z"), NOW), "2026-08-17");
});
