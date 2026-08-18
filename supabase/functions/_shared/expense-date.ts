const MIN_EXPENSE_DATE = "2000-01-01";
const ONE_DAY_MS = 24 * 60 * 60 * 1_000;

function dateInLima(milliseconds: number): string | null {
  if (!Number.isFinite(milliseconds)) return null;
  const date = new Date(milliseconds);
  if (!Number.isFinite(date.getTime())) return null;
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: "America/Lima",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(date);
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

function isSafeExpenseDate(value: string | null, maximum: string): value is string {
  return value !== null && /^\d{4}-\d{2}-\d{2}$/.test(value) && value >= MIN_EXPENSE_DATE && value <= maximum;
}

/**
 * Always supplies a DB-safe non-null date. Receipt dates win when plausible;
 * otherwise Gmail's internal timestamp is interpreted in Lima, then today.
 */
export function expenseDateForReceipt(
  parsedDate: string | null,
  internalDateMilliseconds: number,
  nowMilliseconds = Date.now(),
): string {
  const candidateNow = Number.isFinite(nowMilliseconds) ? new Date(nowMilliseconds) : new Date();
  const now = Number.isFinite(candidateNow.getTime()) ? candidateNow.getTime() : Date.now();
  // PostgreSQL accepts current_date + 1. UTC tomorrow is a conservative upper
  // bound independent of an Edge instance's locale.
  const maximum = new Date(now + ONE_DAY_MS).toISOString().slice(0, 10);
  const today = dateInLima(now)!;
  if (isSafeExpenseDate(parsedDate, maximum)) return parsedDate;
  const messageDate = dateInLima(internalDateMilliseconds);
  return isSafeExpenseDate(messageDate, maximum) ? messageDate : today;
}
