// Receipt parser — extracts an expense amount (+ optional recipient) from an
// email that came from a whitelisted sender (Yape, Peruvian banks, etc.).
//
// DESIGN: this module is deliberately self-contained and free of I/O so it can
// be unit-tested in isolation (`deno test`). The v2 will likely replace the
// regex extraction with an LLM call (OpenAI, same account the WhatsApp bot
// uses); keeping this behind a single `parseReceipt()` boundary means that swap
// touches nothing else.
//
// PRIVACY: only messages from senders in SENDER_WHITELIST are considered. Any
// other email is ignored (returns null) — we never store or transmit mail that
// isn't a payment receipt we explicitly recognize.

export interface ParsedReceipt {
  amount: number;
  /** Merchant / counterparty if confidently extractable, otherwise null. */
  recipient: string | null;
  /** ISO date (YYYY-MM-DD) if present in the email, otherwise null (caller uses today). */
  expenseDate: string | null;
  /** Which whitelist entry matched (for diagnostics / logging). */
  matchedSender: string;
}

export interface EmailForParsing {
  /** Lowercased "From" header value, e.g. "yape <yape@yape.pe>". */
  from: string;
  subject: string;
  /** Plain-text or stripped-HTML body. */
  body: string;
}

/**
 * Senders we trust to be payment receipts. Matched as a case-insensitive
 * substring of the From header. Keep this conservative — false positives create
 * bogus expenses, which is worse than missing a receipt.
 *
 * Add entries here as new institutions are supported. Verify the exact sender
 * domain against a real notification before adding it.
 */
export const SENDER_WHITELIST: readonly string[] = [
  "yape@yape.pe", // Yape (BCP) — payment notifications
  "notificaciones@yape.pe",
  "no-reply@yape.pe",
  // BCP
  "bcplinkseguro.com",
  "@viabcp.com",
  // Interbank
  "@interbank.com.pe",
  // BBVA
  "@bbva.com",
  // Scotiabank
  "@scotiabank.com.pe",
  // Caja Sullana / Credinka / other cajas — add as needed
] as const;

/** A sender is allowed if any whitelist entry appears in the From header. */
export function isWhitelistedSender(from: string): string | null {
  const lower = from.toLowerCase();
  for (const entry of SENDER_WHITELIST) {
    if (lower.includes(entry)) return entry;
  }
  return null;
}

/**
 * Parse a money amount from a string like "S/ 15.50", "S/. 1,200", "PEN 20",
 * "S/25". Returns the numeric value or null if nothing confident is found.
 *
 * Handles Peruvian/Spanish thousand separators (comma) and decimal (dot).
 */
export function parseAmount(text: string): number | null {
  // Amounts prefixed by S/, S/. , PEN — the common Peruvian currency markers.
  // Two number shapes: "1,234.56" (grouped thousands) or "1234.56" (plain).
  const re = /(?:s\/\.?\s*|pen\s*)(\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)/i;
  const m = text.match(re);
  if (!m) return null;
  const raw = m[1].replace(/,/g, ""); // drop thousands separators
  const value = Number(raw);
  if (!Number.isFinite(value) || value <= 0) return null;
  // Sanity bounds: reject absurd values (likely a misparse). A legitimate
  // personal expense in PEN won't exceed this; tune if needed.
  if (value > 50000) return null;
  return value;
}

/** Try to find a date like "15/08/2026" or "2026-08-15" → ISO YYYY-MM-DD. */
export function parseDate(text: string): string | null {
  // dd/mm/yyyy (most common in Peru)
  const dmy = text.match(/\b(\d{1,2})\/(\d{1,2})\/(\d{4})\b/);
  if (dmy) {
    const [, d, m, y] = dmy;
    return `${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`;
  }
  // yyyy-mm-dd
  const ymd = text.match(/\b(\d{4})-(\d{1,2})-(\d{1,2})\b/);
  if (ymd) {
    const [, y, m, d] = ymd;
    return `${y}-${m.padStart(2, "0")}-${d.padStart(2, "0")}`;
  }
  return null;
}

/**
 * Best-effort recipient extraction. For Yape we look for "a <name>" / "de <name>".
 * Returns null if nothing reasonable is found — callers store null rather than
 * a guess.
 */
export function parseRecipient(subject: string, body: string): string | null {
  // Yape-style: "Yapeaste a Carlos" / "Recibiste un Yape de Maria"
  const yape = body.match(/(?:yapeaste\s+a|de\s+un\s+yape\s+de|recibiste\s+un\s+yape\s+de)\s+([A-Za-zÀ-ÿ]{2,40})/i);
  if (yape) return capitalize(yape[1]);
  // Generic "a Nombre Apellido" near a mention of pago/transferencia
  const generic = body.match(/(?:pagaste\s+a|transferiste\s+a|a\s+nombre\s+de)\s+([A-Za-zÀ-ÿ]{2,40})/i);
  if (generic) return capitalize(generic[1]);
  return null;
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}

/**
 * Main entry point. Returns a ParsedReceipt if the email is from a whitelisted
 * sender AND a positive amount could be extracted; otherwise null (ignore).
 *
 * Order: whitelist check FIRST (privacy: don't process unrecognized senders),
 * then amount (the one mandatory field), then optional date/recipient.
 */
export function parseReceipt(email: EmailForParsing): ParsedReceipt | null {
  const matchedSender = isWhitelistedSender(email.from);
  if (!matchedSender) return null;

  // Search both subject and body — some senders put the amount only in subject.
  const haystack = `${email.subject}\n${email.body}`;
  const amount = parseAmount(haystack);
  if (amount === null) return null;

  return {
    amount,
    recipient: parseRecipient(email.subject, email.body),
    expenseDate: parseDate(haystack),
    matchedSender,
  };
}
