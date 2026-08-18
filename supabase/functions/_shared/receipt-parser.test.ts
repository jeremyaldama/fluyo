// Unit tests for the receipt parser. Run with: deno test supabase/functions/_shared/
//
// These tests are the safety net for the most fragile part of the email-import
// feature. If you change a regex, re-run them.

import { assertEquals, assertStrictEquals } from "jsr:@std/assert@1";
import {
  type EmailForParsing,
  hasAlignedGmailDmarcPass,
  isIncomingTransaction,
  isOutgoingTransaction,
  isWhitelistedSender,
  parseAmount,
  parseDate,
  parseReceipt,
  parseRecipient,
  SENDER_WHITELIST,
} from "./receipt-parser.ts";

const YAPE_AUTH_RESULTS = [
  "mx.google.com; dkim=pass header.i=@yape.pe; spf=pass smtp.mailfrom=yape.pe; dmarc=pass (p=REJECT) header.from=yape.pe",
] as const;
const BCP_AUTH_RESULTS = [
  "mx.google.com; dkim=pass header.i=@viabcp.com; dmarc=pass (p=REJECT) header.from=viabcp.com",
] as const;

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

Deno.test("isWhitelistedSender: rejects trusted domain embedded in attacker domain", () => {
  assertStrictEquals(isWhitelistedSender("Cobros <evil@yape.pe.attacker.com>"), null);
  assertStrictEquals(isWhitelistedSender("BCP <alertas@viabcp.com.attacker.net>"), null);
});

Deno.test("isWhitelistedSender: rejects a second mailbox hidden in From", () => {
  assertStrictEquals(isWhitelistedSender("Yape <yape@yape.pe>, attacker@example.com"), null);
});

// ---------- Gmail Authentication-Results / DMARC ----------

Deno.test("hasAlignedGmailDmarcPass: accepts Google's aligned DMARC pass", () => {
  assertStrictEquals(hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", YAPE_AUTH_RESULTS), true);
});

Deno.test("hasAlignedGmailDmarcPass: rejects absent, failed, or unaligned DMARC", () => {
  assertStrictEquals(hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", []), false);
  assertStrictEquals(
    hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", [
      "mx.google.com; dmarc=fail (p=REJECT) header.from=yape.pe",
    ]),
    false,
  );
  assertStrictEquals(
    hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", [
      "mx.google.com; dmarc=pass (p=REJECT) header.from=attacker.example",
    ]),
    false,
  );
});

Deno.test("hasAlignedGmailDmarcPass: ignores a lower spoofed Authentication-Results", () => {
  assertStrictEquals(
    hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", [
      "mx.google.com; dmarc=fail (p=REJECT) header.from=yape.pe",
      "mx.google.com; dmarc=pass (p=REJECT) header.from=yape.pe",
    ]),
    false,
  );
  assertStrictEquals(
    hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", [
      "attacker.example; dmarc=fail header.from=yape.pe",
      "mx.google.com; dmarc=pass header.from=yape.pe",
    ]),
    false,
  );
});

Deno.test("hasAlignedGmailDmarcPass: rejects foreign authserv-id and non-pass variants", () => {
  for (
    const header of [
      "attacker.example; dmarc=pass header.from=yape.pe",
      "mx.google.com.evil; dmarc=pass header.from=yape.pe",
      "mx.google.com; dmarc=none header.from=yape.pe",
      "mx.google.com; dmarc=temperror header.from=yape.pe",
      "mx.google.com; dmarc=bestguesspass header.from=yape.pe",
      "mx.google.com; dmarc=pass header.from=yape.pe.attacker.example",
    ]
  ) {
    assertStrictEquals(hasAlignedGmailDmarcPass("Yape <yape@yape.pe>", [header]), false);
  }
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

Deno.test("parseAmount: comma decimal and localized thousands", () => {
  assertStrictEquals(parseAmount("Monto PEN 42,30"), 42.3);
  assertStrictEquals(parseAmount("Compra por S/ 1.200,50"), 1200.5);
  assertStrictEquals(parseAmount("Compra por S/ 1,200"), 1200);
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

Deno.test("parseAmount: never accepts a partial ambiguous number", () => {
  assertStrictEquals(parseAmount("Monto S/ 1.234"), null);
  assertStrictEquals(parseAmount("Monto PEN 12.345.67"), null);
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

Deno.test("parseDate: rejects impossible dates", () => {
  assertStrictEquals(parseDate("31/02/2026"), null);
  assertStrictEquals(parseDate("99/99/2026"), null);
  assertStrictEquals(parseDate("2026-04-31"), null);
});

// ---------- incoming transaction filter ----------

Deno.test("isIncomingTransaction: recognizes incoming money", () => {
  assertStrictEquals(isIncomingTransaction("Recibiste un Yape por S/ 20"), true);
  assertStrictEquals(isIncomingTransaction("Recibiste un depósito por S/ 30"), true);
  assertStrictEquals(isIncomingTransaction("Recibiste un abono por S/ 40"), true);
  assertStrictEquals(isIncomingTransaction("Transferencia recibida por S/ 50"), true);
  assertStrictEquals(isIncomingTransaction("Abono en tu cuenta por S/ 80"), true);
  assertStrictEquals(isIncomingTransaction("Devolución de compra por S/ 25"), true);
});

Deno.test("isIncomingTransaction: leaves outgoing payments alone", () => {
  assertStrictEquals(isIncomingTransaction("Yapeaste a Carlos por S/ 20"), false);
  assertStrictEquals(isIncomingTransaction("Realizaste un depósito por S/ 50"), false);
});

Deno.test("isOutgoingTransaction: requires a transactional template", () => {
  assertStrictEquals(isOutgoingTransaction("Yapeaste a Carlos por S/ 20"), true);
  assertStrictEquals(isOutgoingTransaction("Compra realizada. Monto S/ 50"), true);
  assertStrictEquals(isOutgoingTransaction("Compra hoy y gana S/ 50"), false);
  assertStrictEquals(isOutgoingTransaction("Promoción con descuento de S/ 20"), false);
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
    authenticationResults: YAPE_AUTH_RESULTS,
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
    authenticationResults: BCP_AUTH_RESULTS,
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
    authenticationResults: YAPE_AUTH_RESULTS,
  };
  assertStrictEquals(parseReceipt(email), null);
});

Deno.test("parseReceipt: amount in subject only", () => {
  const email: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Pago confirmado por S/ 12.00",
    body: "(empty body)",
    authenticationResults: YAPE_AUTH_RESULTS,
  };
  assertStrictEquals(parseReceipt(email)?.amount, 12);
});

Deno.test("parseReceipt: incoming Yape is not recorded as an expense", () => {
  const email: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Recibiste un Yape de Maria",
    body: "Recibiste un Yape por S/ 30.00",
    authenticationResults: YAPE_AUTH_RESULTS,
  };
  assertStrictEquals(parseReceipt(email), null);
});

Deno.test("parseReceipt: spoofed From without aligned DMARC is ignored", () => {
  const base: EmailForParsing = {
    from: "Yape <yape@yape.pe>",
    subject: "Pago confirmado",
    body: "Yapeaste a Carlos por S/ 50.00",
  };
  assertStrictEquals(parseReceipt(base), null);
  assertStrictEquals(
    parseReceipt({
      ...base,
      authenticationResults: ["mx.google.com; dmarc=fail header.from=yape.pe"],
    }),
    null,
  );
});

Deno.test("SENDER_WHITELIST: is non-empty", () => {
  assertEquals(SENDER_WHITELIST.length > 0, true);
  // The list is declared `readonly` (a `as const` tuple), which enforces
  // immutability at the type level — that's the property we rely on.
});
