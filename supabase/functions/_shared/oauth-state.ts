const STATE_VERSION = 1 as const;
const STATE_AAD = new TextEncoder().encode("fluyo:gmail-oauth-state:v1");
const STATE_TTL_SECONDS = 10 * 60;
const MAX_STATE_LENGTH = 4_096;

function asArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

export interface OAuthStatePayload {
  version: typeof STATE_VERSION;
  userId: string;
  redirectUri: string;
  codeVerifier: string;
  nonce: string;
  issuedAt: number;
  expiresAt: number;
}

function base64UrlEncode(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlDecode(value: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_base64url");
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  const binary = atob(padded);
  const decoded = Uint8Array.from(binary, (char) => char.charCodeAt(0));
  // Reject alternate encodings that differ only in unused base64 padding bits.
  if (base64UrlEncode(decoded) !== value) throw new Error("invalid_base64url");
  return decoded;
}

async function stateKey(secret: string): Promise<CryptoKey> {
  if (new TextEncoder().encode(secret).byteLength < 32) {
    throw new Error("GMAIL_OAUTH_STATE_SECRET must be at least 32 bytes");
  }
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(secret));
  return crypto.subtle.importKey("raw", digest, "AES-GCM", false, ["encrypt", "decrypt"]);
}

export function randomBase64Url(byteLength: number): string {
  if (!Number.isSafeInteger(byteLength) || byteLength < 16 || byteLength > 128) {
    throw new Error("invalid_random_length");
  }
  return base64UrlEncode(crypto.getRandomValues(new Uint8Array(byteLength)));
}

export async function createPkcePair(): Promise<{ verifier: string; challenge: string }> {
  const verifier = randomBase64Url(32);
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(verifier));
  return { verifier, challenge: base64UrlEncode(new Uint8Array(digest)) };
}

export async function sealOAuthState(
  secret: string,
  input: Pick<OAuthStatePayload, "userId" | "redirectUri" | "codeVerifier">,
  nowSeconds = Math.floor(Date.now() / 1_000),
): Promise<string> {
  const payload: OAuthStatePayload = {
    version: STATE_VERSION,
    userId: input.userId,
    redirectUri: input.redirectUri,
    codeVerifier: input.codeVerifier,
    nonce: randomBase64Url(24),
    issuedAt: nowSeconds,
    expiresAt: nowSeconds + STATE_TTL_SECONDS,
  };
  validatePayload(payload, nowSeconds, false);

  const iv = crypto.getRandomValues(new Uint8Array(12));
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv: asArrayBuffer(iv), additionalData: asArrayBuffer(STATE_AAD), tagLength: 128 },
    await stateKey(secret),
    new TextEncoder().encode(JSON.stringify(payload)),
  );
  return `v1.${base64UrlEncode(iv)}.${base64UrlEncode(new Uint8Array(ciphertext))}`;
}

export async function openOAuthState(
  secret: string,
  state: string,
  nowSeconds = Math.floor(Date.now() / 1_000),
): Promise<OAuthStatePayload> {
  if (!state || state.length > MAX_STATE_LENGTH) throw new Error("invalid_state");
  const [version, encodedIv, encodedCiphertext, extra] = state.split(".");
  if (version !== "v1" || !encodedIv || !encodedCiphertext || extra !== undefined) {
    throw new Error("invalid_state");
  }
  let iv: Uint8Array;
  let ciphertext: Uint8Array;
  try {
    iv = base64UrlDecode(encodedIv);
    ciphertext = base64UrlDecode(encodedCiphertext);
  } catch {
    throw new Error("invalid_state");
  }
  if (iv.byteLength !== 12 || ciphertext.byteLength < 17 || ciphertext.byteLength > 3_072) {
    throw new Error("invalid_state");
  }

  let plaintext: ArrayBuffer;
  try {
    plaintext = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv: asArrayBuffer(iv), additionalData: asArrayBuffer(STATE_AAD), tagLength: 128 },
      await stateKey(secret),
      asArrayBuffer(ciphertext),
    );
  } catch {
    throw new Error("invalid_state");
  }

  let payload: unknown;
  try {
    payload = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(plaintext));
  } catch {
    throw new Error("invalid_state");
  }
  validatePayload(payload, nowSeconds, true);
  return payload;
}

function validatePayload(
  value: unknown,
  nowSeconds: number,
  enforceTime: boolean,
): asserts value is OAuthStatePayload {
  if (!value || typeof value !== "object") throw new Error("invalid_state");
  const payload = value as Record<string, unknown>;
  if (
    payload.version !== STATE_VERSION ||
    typeof payload.userId !== "string" ||
    !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(payload.userId) ||
    typeof payload.redirectUri !== "string" ||
    payload.redirectUri.length > 512 ||
    typeof payload.codeVerifier !== "string" ||
    !/^[A-Za-z0-9._~-]{43,128}$/.test(payload.codeVerifier) ||
    typeof payload.nonce !== "string" ||
    !/^[A-Za-z0-9_-]{32}$/.test(payload.nonce) ||
    !Number.isSafeInteger(payload.issuedAt) ||
    !Number.isSafeInteger(payload.expiresAt)
  ) {
    throw new Error("invalid_state");
  }

  const issuedAt = payload.issuedAt as number;
  const expiresAt = payload.expiresAt as number;
  if (expiresAt - issuedAt !== STATE_TTL_SECONDS) throw new Error("invalid_state");
  if (enforceTime && (issuedAt > nowSeconds + 60 || expiresAt < nowSeconds)) {
    throw new Error(expiresAt < nowSeconds ? "expired_state" : "invalid_state");
  }
}
