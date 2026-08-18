const GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
const MAX_JWT_LENGTH = 16_384;
const CLOCK_SKEW_SECONDS = 60;
const MAX_TOKEN_LIFETIME_SECONDS = 3_700;
const UNKNOWN_KID_REFRESH_COOLDOWN_MS = 60_000;

interface GoogleJwk extends JsonWebKey {
  kid?: string;
  alg?: string;
  use?: string;
}

export interface GoogleOidcClaims {
  iss: string;
  aud: string | string[];
  sub: string;
  email: string;
  email_verified: boolean;
  iat: number;
  exp: number;
}

export interface GoogleOidcExpectations {
  audience: string;
  serviceAccountEmail: string;
}

let cachedKeys = new Map<string, GoogleJwk>();
let keysExpireAtMs = 0;
let keysLoadInFlight: Promise<Map<string, GoogleJwk>> | null = null;
let unknownKidRefreshAllowedAtMs = 0;

function decodeBase64Url(value: string): Uint8Array {
  if (!value || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("invalid_jwt");
  const padded = value.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - value.length % 4) % 4);
  let binary: string;
  try {
    binary = atob(padded);
  } catch {
    throw new Error("invalid_jwt");
  }
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

function asArrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.buffer.slice(bytes.byteOffset, bytes.byteOffset + bytes.byteLength) as ArrayBuffer;
}

function decodeJsonPart(value: string): Record<string, unknown> {
  try {
    const bytes = decodeBase64Url(value);
    if (bytes.byteLength > 8_192) throw new Error("invalid_jwt");
    const parsed = JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("invalid_jwt");
    return parsed;
  } catch {
    throw new Error("invalid_jwt");
  }
}

function cacheMaxAgeMs(header: string | null): number {
  const seconds = header?.match(/(?:^|,)\s*max-age=(\d+)/i)?.[1];
  const parsed = seconds ? Number(seconds) : 300;
  return Math.min(Math.max(parsed, 60), 86_400) * 1_000;
}

async function loadKeys(
  fetchImpl: typeof fetch,
  force: boolean,
  nowMs: () => number,
): Promise<Map<string, GoogleJwk>> {
  if (!force && cachedKeys.size > 0 && nowMs() < keysExpireAtMs) return cachedKeys;
  if (keysLoadInFlight) return await keysLoadInFlight;

  const load = (async () => {
    const response = await fetchImpl(GOOGLE_JWKS_URL, {
      headers: { Accept: "application/json" },
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) throw new Error("google_jwks_unavailable");
    const json = await response.json() as { keys?: GoogleJwk[] };
    if (!Array.isArray(json.keys)) throw new Error("google_jwks_invalid");

    const next = new Map<string, GoogleJwk>();
    for (const key of json.keys) {
      if (
        key && key.kty === "RSA" && key.alg === "RS256" && key.use === "sig" &&
        typeof key.kid === "string" && key.kid.length > 0
      ) {
        next.set(key.kid, key);
      }
    }
    if (next.size === 0) throw new Error("google_jwks_invalid");
    cachedKeys = next;
    keysExpireAtMs = nowMs() + cacheMaxAgeMs(response.headers.get("cache-control"));
    return cachedKeys;
  })();
  keysLoadInFlight = load;

  try {
    return await load;
  } finally {
    if (keysLoadInFlight === load) keysLoadInFlight = null;
  }
}

async function findKey(kid: string, fetchImpl: typeof fetch, nowMs: () => number): Promise<GoogleJwk> {
  let keys = await loadKeys(fetchImpl, false, nowMs);
  let key = keys.get(kid);
  if (key) return key;

  // A concurrent request may already be refreshing after its own cache miss.
  // Join it so legitimate key rotation succeeds without another network call.
  if (keysLoadInFlight) {
    keys = await keysLoadInFlight;
    key = keys.get(kid);
    if (key) return key;
  }

  const refreshStartedAtMs = nowMs();
  if (refreshStartedAtMs < unknownKidRefreshAllowedAtMs) {
    throw new Error("unknown_google_signing_key");
  }

  // Set the global cooldown before yielding so concurrent unknown kids cannot
  // fan out requests. Failed refreshes are rate-limited too.
  unknownKidRefreshAllowedAtMs = refreshStartedAtMs + UNKNOWN_KID_REFRESH_COOLDOWN_MS;
  keys = await loadKeys(fetchImpl, true, nowMs);
  key = keys.get(kid);
  if (!key) throw new Error("unknown_google_signing_key");
  return key;
}

export function validateGoogleOidcClaims(
  raw: Record<string, unknown>,
  expected: GoogleOidcExpectations,
  nowSeconds = Math.floor(Date.now() / 1_000),
): GoogleOidcClaims {
  const issuer = raw.iss;
  const audience = raw.aud;
  const audienceMatches = typeof audience === "string"
    ? audience === expected.audience
    : Array.isArray(audience) && audience.length === 1 && audience[0] === expected.audience;
  const issuedAt = raw.iat;
  const expiresAt = raw.exp;
  if (
    (issuer !== "https://accounts.google.com" && issuer !== "accounts.google.com") ||
    !audienceMatches ||
    typeof raw.sub !== "string" || raw.sub.length === 0 ||
    typeof raw.email !== "string" ||
    raw.email.toLowerCase() !== expected.serviceAccountEmail.toLowerCase() ||
    raw.email_verified !== true ||
    !Number.isSafeInteger(issuedAt) ||
    !Number.isSafeInteger(expiresAt)
  ) {
    throw new Error("invalid_google_oidc_claims");
  }
  const iat = issuedAt as number;
  const exp = expiresAt as number;
  if (
    iat > nowSeconds + CLOCK_SKEW_SECONDS ||
    exp <= nowSeconds - CLOCK_SKEW_SECONDS ||
    exp <= iat ||
    exp - iat > MAX_TOKEN_LIFETIME_SECONDS ||
    iat < nowSeconds - MAX_TOKEN_LIFETIME_SECONDS - CLOCK_SKEW_SECONDS
  ) {
    throw new Error("expired_google_oidc_token");
  }
  return raw as unknown as GoogleOidcClaims;
}

export async function verifyGoogleOidcJwt(
  token: string,
  expected: GoogleOidcExpectations,
  options: { fetchImpl?: typeof fetch; nowSeconds?: number; nowMs?: () => number } = {},
): Promise<GoogleOidcClaims> {
  if (!token || token.length > MAX_JWT_LENGTH) throw new Error("invalid_jwt");
  const [encodedHeader, encodedPayload, encodedSignature, extra] = token.split(".");
  if (!encodedHeader || !encodedPayload || !encodedSignature || extra !== undefined) {
    throw new Error("invalid_jwt");
  }
  const header = decodeJsonPart(encodedHeader);
  if (header.alg !== "RS256" || typeof header.kid !== "string" || header.kid.length > 256) {
    throw new Error("invalid_jwt_header");
  }

  const fetchImpl = options.fetchImpl ?? fetch;
  const jwk = await findKey(header.kid, fetchImpl, options.nowMs ?? Date.now);
  const key = await crypto.subtle.importKey(
    "jwk",
    jwk,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["verify"],
  );
  const valid = await crypto.subtle.verify(
    "RSASSA-PKCS1-v1_5",
    key,
    asArrayBuffer(decodeBase64Url(encodedSignature)),
    new TextEncoder().encode(`${encodedHeader}.${encodedPayload}`),
  );
  if (!valid) throw new Error("invalid_google_oidc_signature");

  return validateGoogleOidcClaims(
    decodeJsonPart(encodedPayload),
    expected,
    options.nowSeconds,
  );
}

/** Test isolation only; production code never needs to clear Google's cached JWKS. */
export function clearGoogleJwksCacheForTests(): void {
  cachedKeys = new Map();
  keysExpireAtMs = 0;
  keysLoadInFlight = null;
  unknownKidRefreshAllowedAtMs = 0;
}
