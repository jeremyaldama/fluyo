const DEDUPE_SECRET = Deno.env.get("GMAIL_DEDUPE_SECRET") ?? "";

function base64Url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function bytes(value: string): Uint8Array<ArrayBuffer> {
  return new TextEncoder().encode(value) as Uint8Array<ArrayBuffer>;
}

export async function gmailMailboxFingerprint(
  email: string,
  secret = DEDUPE_SECRET,
): Promise<string> {
  const normalizedEmail = email.trim().toLowerCase();
  if (!/^[^@\s]{1,64}@[^@\s]{1,253}$/.test(normalizedEmail)) throw new Error("invalid_gmail_address");
  if (bytes(secret).byteLength < 32) throw new Error("GMAIL_DEDUPE_SECRET must be at least 32 bytes");
  const key = await crypto.subtle.importKey(
    "raw",
    bytes(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, bytes(normalizedEmail));
  return base64Url(new Uint8Array(signature));
}

export async function gmailMailboxReferencePrefix(
  email: string,
  secret = DEDUPE_SECRET,
): Promise<string> {
  return `gmail:v1:${await gmailMailboxFingerprint(email, secret)}`;
}
