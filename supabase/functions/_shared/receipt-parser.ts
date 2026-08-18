// Receipt parser — extracts an expense amount (+ optional recipient) from an
// email that came from a whitelisted sender (Yape, Peruvian banks, etc.).
//
// DESIGN: this module is deliberately self-contained and free of I/O so it can
// be unit-tested in isolation (`deno test`). Keeping the rules behind one
// `parseReceipt()` boundary lets supported receipt formats evolve together with
// real-message fixtures, without spreading parsing logic through the webhook.
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
  /** Authentication-Results headers in original top-to-bottom message order. */
  authenticationResults?: readonly string[];
}

// Supported transactional senders: Yape exact mailboxes plus authenticated
// BCP, Interbank, BBVA and Scotiabank domains. New institutions must ship with
// a real-message fixture, aligned DMARC and an outgoing transaction template.
const TRUSTED_EXACT_ADDRESSES = new Set([
  "yape@yape.pe",
  "notificaciones@yape.pe",
  "no-reply@yape.pe",
]);

const TRUSTED_DOMAINS = new Set([
  "bcplinkseguro.com",
  "viabcp.com",
  "interbank.com.pe",
  "bbva.com",
  "scotiabank.com.pe",
]);

/** Public diagnostic view kept immutable for tests and operational visibility. */
export const SENDER_WHITELIST: readonly string[] = [
  ...TRUSTED_EXACT_ADDRESSES,
  ...[...TRUSTED_DOMAINS].map((domain) => `@${domain}`),
] as const;

/**
 * Extract exactly one RFC-5322-ish mailbox from From. Gmail already unfolds the
 * header; accepting more than one address would make display-name tricks and
 * group syntax ambiguous, so those messages are rejected conservatively.
 */
export function extractSenderAddress(from: string): string | null {
  if (!from || from.length > 998 || /[\r\n]/.test(from)) return null;
  const candidates = [
    ...from.toLowerCase().matchAll(
      /(?:^|[<\s,(])([a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]{0,251}[a-z0-9])?)(?=[>\s,)]|$)/g,
    ),
  ]
    .map((match) => match[1])
    .filter((address, index, all) => all.indexOf(address) === index);
  return candidates.length === 1 ? candidates[0] ?? null : null;
}

/** A sender is allowed only on an exact address/domain boundary. */
export function isWhitelistedSender(from: string): string | null {
  const address = extractSenderAddress(from);
  if (!address) return null;
  if (TRUSTED_EXACT_ADDRESSES.has(address)) return address;
  const domain = address.slice(address.lastIndexOf("@") + 1);
  for (const trustedDomain of TRUSTED_DOMAINS) {
    if (domain === trustedDomain || domain.endsWith(`.${trustedDomain}`)) {
      return `@${trustedDomain}`;
    }
  }
  return null;
}

/**
 * Gmail prepends its own Authentication-Results header at the top of received
 * mail. We use only the first mx.google.com result and require an aligned DMARC
 * pass; lower, sender-supplied Authentication-Results headers are ignored.
 */
export function hasAlignedGmailDmarcPass(
  from: string,
  authenticationResults: readonly string[],
): boolean {
  const address = extractSenderAddress(from);
  if (!address) return false;
  const fromDomain = canonicalDomain(address.slice(address.lastIndexOf("@") + 1));
  if (!fromDomain) return false;

  const trustedResult = authenticationResults[0];
  // Gmail prepends its result. Never search lower headers: an attacker can add
  // Authentication-Results to the original message before Gmail receives it.
  if (!trustedResult || !/^\s*mx\.google\.com\s*;/i.test(trustedResult)) return false;
  const unfolded = trustedResult.replace(/\r?\n[\t ]+/g, " ");
  for (const segment of unfolded.split(";").slice(1)) {
    if (!/^\s*dmarc\s*=\s*pass\b/i.test(segment)) continue;
    const match = segment.match(/\bheader\.from\s*=\s*"?([a-z0-9.-]+)"?/i);
    const authenticatedDomain = canonicalDomain(match?.[1] ?? "");
    return authenticatedDomain === fromDomain;
  }
  return false;
}

function canonicalDomain(value: string): string | null {
  const domain = value.trim().toLowerCase().replace(/\.$/, "");
  if (
    domain.length < 3 || domain.length > 253 || !domain.includes(".") ||
    !domain.split(".").every((label) => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(label))
  ) return null;
  return domain;
}

/**
 * Parse a money amount from a string like "S/ 15.50", "S/. 1,200", "PEN 20",
 * "S/25". Returns the numeric value or null if nothing confident is found.
 *
 * Handles Peruvian/Spanish thousand separators (comma) and decimal (dot).
 */
export function parseAmount(text: string): number | null {
  // Accept both 1,234.56 and the common localized 1.234,56 / 15,50 forms.
  const re =
    /(?:s\/\.?\s*|pen\s*)(\d{1,3}(?:\.\d{3})+,\d{1,2}|\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?![\d.,])/i;
  const m = text.match(re);
  if (!m) return null;
  const raw = m[1]!;
  const normalized = /^\d{1,3}(?:\.\d{3})+,\d{1,2}$/.test(raw)
    ? raw.replace(/\./g, "").replace(",", ".")
    : /^\d{1,3}(?:,\d{3})+(?:\.\d{1,2})?$/.test(raw)
    ? raw.replace(/,/g, "")
    : raw.replace(",", ".");
  const value = Number(normalized);
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
    return validIsoDate(Number(y), Number(m), Number(d));
  }
  // yyyy-mm-dd
  const ymd = text.match(/\b(\d{4})-(\d{1,2})-(\d{1,2})\b/);
  if (ymd) {
    const [, y, m, d] = ymd;
    return validIsoDate(Number(y), Number(m), Number(d));
  }
  return null;
}

function validIsoDate(year: number, month: number, day: number): string | null {
  if (year < 2000 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) return null;
  const date = new Date(Date.UTC(year, month - 1, day));
  if (
    date.getUTCFullYear() !== year ||
    date.getUTCMonth() !== month - 1 ||
    date.getUTCDate() !== day
  ) return null;
  return `${String(year).padStart(4, "0")}-${String(month).padStart(2, "0")}-${String(day).padStart(2, "0")}`;
}

/** Incoming money is not an expense, even when the sender and amount are valid. */
export function isIncomingTransaction(text: string): boolean {
  return /\b(?:reembolso|devoluci[oó]n|recibiste\s+(?:un\s+)?(?:yape|dep[oó]sito|abono|pago|transferencia)|(?:pago|transferencia)\s+recibid[oa]|dep[oó]sito\s+recibido|abono\s+(?:recibido|en\s+tu\s+cuenta)|te\s+(?:depositaron|transfirieron)|dinero\s+recibido)\b/i
    .test(text);
}

/** Conservative outgoing-payment templates; generic promotions do not qualify. */
export function isOutgoingTransaction(text: string): boolean {
  return /\b(?:yapeaste\s+a|pagaste\s+a|transferiste\s+a|realizaste\s+(?:un(?:a)?\s+)?(?:pago|compra|transferencia)|(?:pago|compra|consumo|operaci[oó]n|transacci[oó]n)\s+(?:realizad[ao]|aprobad[ao]|confirmad[ao]|procesad[ao])|se\s+(?:realiz[oó]|proces[oó]|confirm[oó])\s+(?:tu\s+)?(?:pago|compra|transferencia))\b/i
    .test(text);
}

/**
 * Best-effort recipient extraction. For Yape we look for "a <name>" / "de <name>".
 * Returns null if nothing reasonable is found — callers store null rather than
 * a guess.
 */
export function parseRecipient(_subject: string, body: string): string | null {
  // Yape-style: "Yapeaste a Carlos" / "Recibiste un Yape de Maria"
  const yape = body.match(/(?:yapeaste\s+a|de\s+un\s+yape\s+de|recibiste\s+un\s+yape\s+de)\s+([A-Za-zÀ-ÿ]{2,40})/i);
  if (yape) return capitalize(yape[1]!);
  // Generic "a Nombre Apellido" near a mention of pago/transferencia
  const generic = body.match(/(?:pagaste\s+a|transferiste\s+a|a\s+nombre\s+de)\s+([A-Za-zÀ-ÿ]{2,40})/i);
  if (generic) return capitalize(generic[1]!);
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
  if (!hasAlignedGmailDmarcPass(email.from, email.authenticationResults ?? [])) return null;

  // Search both subject and body — some senders put the amount only in subject.
  const haystack = `${email.subject}\n${email.body}`;
  if (isIncomingTransaction(haystack)) return null;
  if (!isOutgoingTransaction(haystack)) return null;
  const amount = parseAmount(haystack);
  if (amount === null) return null;

  return {
    amount,
    recipient: parseRecipient(email.subject, email.body),
    expenseDate: parseDate(haystack),
    matchedSender,
  };
}
