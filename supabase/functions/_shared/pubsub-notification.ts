const MAX_ENCODED_DATA_LENGTH = 8_192;

const CANONICAL_BASE64 = /^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$/;

export interface GmailNotification {
  emailAddress: string;
  historyId: string;
}

function decodeCanonicalBase64Json(data: string): unknown {
  if (!data || data.length > MAX_ENCODED_DATA_LENGTH || !CANONICAL_BASE64.test(data)) {
    throw new Error("invalid_data");
  }

  const binary = atob(data);
  // atob accepts encodings with non-zero unused padding bits. Re-encoding the
  // bytes makes the accepted representation unique and rejects those aliases.
  if (btoa(binary) !== data) throw new Error("invalid_data");

  const bytes = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  const json = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
  return JSON.parse(json);
}

/** Decode and validate the standard-Base64 data field from a Gmail Pub/Sub push. */
export function decodeGmailPubSubData(data: string): GmailNotification | null {
  try {
    const parsed = decodeCanonicalBase64Json(data);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return null;
    const record = parsed as Record<string, unknown>;
    if (
      typeof record.emailAddress !== "string" ||
      record.emailAddress.length > 320 ||
      !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(record.emailAddress) ||
      typeof record.historyId !== "string" ||
      !/^\d{1,64}$/.test(record.historyId)
    ) return null;
    return { emailAddress: record.emailAddress.trim().toLowerCase(), historyId: record.historyId };
  } catch {
    return null;
  }
}
