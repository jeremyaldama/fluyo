// Unit tests for the receipt parser. Run with: deno test supabase/functions/_shared/
//
// These tests are the safety net for the most fragile part of the email-import
// feature. If you change a regex, re-run them.

import {
  assertEquals,
  assertStrictEquals,
} from "jsr:@std/assert@1";
import {
  isWhitelistedSender,
  parseAmount,
  parseDate,
  parseReceipt,
  parseRecipient,
  SENDER_WHITELIST,
  type EmailForParsing,
} from "./receipt-parser.ts";

// ---------- isWhitelistedSender ----------

Deno.test("isWhitelistedSender: matches Yape sender", () => {
  assertStrictEquals(isWhitelistedSender("Yape <yape@yape.pe>"), "yape@yape.pe");
});

Deno.test("isWhitelistedSender: case-insensitive", () => {
  assertStrictEquals(isWhitelistedSender("YAPE@YAPE.PE"), "yape@yape.pe");
});

Deno.test("isWhitelistedSender: matches BCP viaabcp domain", () => {
  assertStrictEquals(
    isWhitelistedSender("BCP <alertas@viabcp.com>"),
    "@viabcp.com",
  );
});

Deno.test("isWhitelistedSender: rejects unknown sender", () => {
  assertStrictEquals(isWhitelistedSender("amigo <amigo@gmail.com>"), null);
});

Deno.test("isWhitelistedSender: rejects newsletter spam", () => {
  assertStrictEquals(isWhitelistedSender("promo@mercadolibre.com"), null);
});

// ---------- parseAmount ----------

Deno.test("parseAmount: S/ with decimal", () => {
  assertStrictEquals(parseAmount("Pagaste S/ 15.50"), 15.5);
});

Deno.test("parseAmount: S/ without space", () => {
  assertStrictEquals(parseAmount("Monto: S/25"), 25);
});

Deno.test("parseAmount: S/. variant", () => {
  assertStrictEquals(parseAmount("Total S/. 120.00"), 120);
});

Deno.test("parseAmount: thousands separator", () => {
  assertStrictEquals(parseAmount("Compra por S/ 1,200.00"), 1200);
});

Deno.test("parseAmount: PEN prefix", () => {
  assertStrictEquals(parseAmount("Monto PEN 42.30"), 42.3);
});

Deno.test("parseAmount: picks first amount in haystack", () => {
  // When multiple amounts appear, the first currency-prefixed one wins.
  assertStrictEquals(parseAmount("S/ 10.00 y luego S/ 20.00"), 10);
});

Deno.test("parseAmount: returns null when no currency marker", () => {
  assertStrictEquals(parseAmount("tienes 15 soles"), null);
});

Deno.test("parseAmount: rejects zero", () => {
  assertStrictEquals(parseAmount("S/ 0.00"), null);
});

Deno.test("parseAmount: rejects absurd value (misparse guard)", () => {
  assertStrictEquals(parseAmount("S/ 999999"), null);
});

// ---------- parseDate ----------

Deno.test("parseDate: dd/mm/yyyy (Peru standard)", () => {
  assertStrictEquals(parseDate("Fecha: 15/08/2026"), "2026-08-15");
});

Deno.test("parseDate: yyyy-mm-dd", () => {
  assertStrictEquals(parseDate("2026-12-01"), "2026-12-01");
});

Deno.test("parseDate: pads single digit day/month", () => {
  assertStrictEquals(parseDate("3/4/2026"), "2026-04-03");
});

Deno.test("parseDate: null when absent", () => {
  assertStrictEquals(parseDate("no date here"), null);
});

// ---------- parseRecipient ----------

Deno.test("parseRecipient: Yape 'yapeaste a'", () => {
  assertStrictEquals(parseRecipient("", "Yapeaste a Carlos por S/ 20"), "Carlos");
});

Deno.test("parseRecipient: 'recibiste un yape de'", () => {
  assertStrictEquals(
    parseRecipient("", "Recibiste un Yape de Maria"),
    "Maria",
  );
});

Deno.test("parseRecipient: 'pagaste a'", () => {
  assertStrictEquals(parseRecipient("", "Pagaste a Tottus S/ 50"), "Tottus");
});

Deno.test("parseRecipient: null when nothing matches", () => {
  assertStrictEquals(parseRecipient("", "compra realizada"), null);
});

// ---------- parseReceipt (integration) ----------

Deno.test("parseReceipt: full Yape payment", () => {
  const email: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Yape confirmado",
    body: "Yapeaste a Carlos por S/ 20.00 el 15/08/2026",
  };
  assertEquals(parseReceipt(email), {
    amount: 20,
    recipient: "Carlos",
    expenseDate: "2026-08-15",
    matchedSender: "yape@yape.pe",
  });
});

Deno.test("parseReceipt: bank notification, no recipient", () => {
  const email: EmailForParsing = {
    from: "BCP <alertas@viabcp.com>",
    subject: "Comprobante de pago",
    body: "Compra realizada. Monto: S/ 135.90",
  };
  assertEquals(parseReceipt(email), {
    amount: 135.9,
    recipient: null,
    expenseDate: null,
    matchedSender: "@viabcp.com",
  });
});

Deno.test("parseReceipt: ignores non-whitelisted sender (privacy)", () => {
  const email: EmailForParsing = {
    from: "amigo <amigo@gmail.com>",
    subject: "te debo",
    body: "S/ 50 por el almuerzo",
  };
  // Even though there's an amount, sender isn't trusted → ignored entirely.
  assertStrictEquals(parseReceipt(email), null);
});

Deno.test("parseReceipt: whitelisted sender but no amount → null", () => {
  const email: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Bienvenido a Yape",
    body: "Gracias por usar Yape. Activa tu cuenta.",
  };
  assertStrictEquals(parseReceipt(email), null);
});

Deno.test("parseReceipt: amount in subject only", () => {
  const email: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Pago de S/ 12.00 recibido",
    body: "(empty body)",
  };
  assertStrictEquals(parseReceipt(email)?.amount, 12);
});

Deno.test("SENDER_WHITELIST: is non-empty", () => {
  assertEquals(SENDER_WHITELIST.length > 0, true);
  // The list is declared `readonly` (a `as const` tuple), which enforces
  // immutability at the type level — that's the property we rely on.
});
