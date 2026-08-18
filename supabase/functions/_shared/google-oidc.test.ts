import { assertEquals, assertRejects, assertThrows } from "jsr:@std/assert@1";
import { clearGoogleJwksCacheForTests, validateGoogleOidcClaims, verifyGoogleOidcJwt } from "./google-oidc.ts";

const NOW = 1_700_000_000;
const EXPECTED = {
  audience: "https://example.supabase.co/functions/v1/gmail-webhook",
  serviceAccountEmail: "gmail-push@example.iam.gserviceaccount.com",
};

function b64url(bytes: Uint8Array): string {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function encodeJson(value: unknown): string {
  return b64url(new TextEncoder().encode(JSON.stringify(value)));
}

interface SigningFixture {
  token: string;
  publicJwk: JsonWebKey & { kid: string; alg: "RS256"; use: "sig" };
}

async function signingFixture(kid: string): Promise<SigningFixture> {
  const pair = await crypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"],
  );
  const publicJwk = await crypto.subtle.exportKey("jwk", pair.publicKey);
  const header = encodeJson({ alg: "RS256", typ: "JWT", kid });
  const payload = encodeJson({
    iss: "https://accounts.google.com",
    aud: EXPECTED.audience,
    sub: "1234",
    email: EXPECTED.serviceAccountEmail,
    email_verified: true,
    iat: NOW - 30,
    exp: NOW + 3_000,
  });
  const signingInput = `${header}.${payload}`;
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    pair.privateKey,
    new TextEncoder().encode(signingInput),
  );
  return {
    token: `${signingInput}.${b64url(new Uint8Array(signature))}`,
    publicJwk: { ...publicJwk, kid, alg: "RS256", use: "sig" },
  };
}

function tokenWithUnknownKid(kid: string): string {
  return `${encodeJson({ alg: "RS256", typ: "JWT", kid })}.${encodeJson({})}.AQ`;
}

function jwksResponse(keys: JsonWebKey[], maxAgeSeconds = 3_600): Response {
  return new Response(
    JSON.stringify({ keys }),
    { headers: { "cache-control": `public, max-age=${maxAgeSeconds}` } },
  );
}

Deno.test("Google OIDC verifier checks signature, audience and service account", async () => {
  clearGoogleJwksCacheForTests();
  const fixture = await signingFixture("test-key");
  const mockFetch = (() =>
    Promise.resolve(
      jwksResponse([fixture.publicJwk]),
    )) as typeof fetch;

  const claims = await verifyGoogleOidcJwt(fixture.token, EXPECTED, { fetchImpl: mockFetch, nowSeconds: NOW });
  assertEquals(claims.email, EXPECTED.serviceAccountEmail);
});

Deno.test("Google OIDC verifier coalesces concurrent JWKS loads", async () => {
  clearGoogleJwksCacheForTests();
  const fixture = await signingFixture("concurrent-key");
  let fetchCalls = 0;
  let releaseFetch: (response: Response) => void = () => {
    throw new Error("fetch_not_initialized");
  };
  const pendingResponse = new Promise<Response>((resolve) => {
    releaseFetch = resolve;
  });
  const mockFetch = (() => {
    fetchCalls += 1;
    return pendingResponse;
  }) as typeof fetch;

  const verifications = Array.from(
    { length: 16 },
    () => verifyGoogleOidcJwt(fixture.token, EXPECTED, { fetchImpl: mockFetch, nowSeconds: NOW }),
  );
  assertEquals(fetchCalls, 1);
  releaseFetch(jwksResponse([fixture.publicJwk]));

  const claims = await Promise.all(verifications);
  assertEquals(claims.length, 16);
  assertEquals(claims.every((claim) => claim.email === EXPECTED.serviceAccountEmail), true);
  assertEquals(fetchCalls, 1);
});

Deno.test("unknown Google signing keys share one forced refresh and respect the global cooldown", async () => {
  clearGoogleJwksCacheForTests();
  const known = await signingFixture("known-key");
  let currentTimeMs = 1_000_000;
  let fetchCalls = 0;
  const mockFetch = (() => {
    fetchCalls += 1;
    return Promise.resolve(jwksResponse([known.publicJwk]));
  }) as typeof fetch;
  const options = {
    fetchImpl: mockFetch,
    nowSeconds: NOW,
    nowMs: () => currentTimeMs,
  };

  const attempts = Array.from(
    { length: 16 },
    (_, index) => verifyGoogleOidcJwt(tokenWithUnknownKid(`unknown-${index}`), EXPECTED, options),
  );
  const results = await Promise.allSettled(attempts);
  assertEquals(
    results.every((result) =>
      result.status === "rejected" && result.reason instanceof Error &&
      result.reason.message === "unknown_google_signing_key"
    ),
    true,
  );
  assertEquals(fetchCalls, 2);

  await assertRejects(
    () => verifyGoogleOidcJwt(tokenWithUnknownKid("during-cooldown"), EXPECTED, options),
    Error,
    "unknown_google_signing_key",
  );
  currentTimeMs += 59_999;
  await assertRejects(
    () => verifyGoogleOidcJwt(tokenWithUnknownKid("still-cooling-down"), EXPECTED, options),
    Error,
    "unknown_google_signing_key",
  );
  assertEquals(fetchCalls, 2);

  currentTimeMs += 1;
  await assertRejects(
    () => verifyGoogleOidcJwt(tokenWithUnknownKid("next-window"), EXPECTED, options),
    Error,
    "unknown_google_signing_key",
  );
  assertEquals(fetchCalls, 3);
});

Deno.test("an unknown kid can trigger a coalesced JWKS rotation refresh", async () => {
  clearGoogleJwksCacheForTests();
  const oldFixture = await signingFixture("old-key");
  const newFixture = await signingFixture("new-key");
  let fetchCalls = 0;
  const mockFetch = (() => {
    fetchCalls += 1;
    return Promise.resolve(jwksResponse(fetchCalls === 1 ? [oldFixture.publicJwk] : [newFixture.publicJwk]));
  }) as typeof fetch;

  await verifyGoogleOidcJwt(oldFixture.token, EXPECTED, { fetchImpl: mockFetch, nowSeconds: NOW });
  const claims = await verifyGoogleOidcJwt(newFixture.token, EXPECTED, { fetchImpl: mockFetch, nowSeconds: NOW });

  assertEquals(claims.email, EXPECTED.serviceAccountEmail);
  assertEquals(fetchCalls, 2);
});

Deno.test("Google JWKS still refreshes normally when its cache TTL expires", async () => {
  clearGoogleJwksCacheForTests();
  const fixture = await signingFixture("ttl-key");
  let currentTimeMs = 2_000_000;
  let fetchCalls = 0;
  const mockFetch = (() => {
    fetchCalls += 1;
    return Promise.resolve(jwksResponse([fixture.publicJwk], 60));
  }) as typeof fetch;
  const options = {
    fetchImpl: mockFetch,
    nowSeconds: NOW,
    nowMs: () => currentTimeMs,
  };

  await verifyGoogleOidcJwt(fixture.token, EXPECTED, options);
  currentTimeMs += 59_999;
  await verifyGoogleOidcJwt(fixture.token, EXPECTED, options);
  assertEquals(fetchCalls, 1);

  currentTimeMs += 1;
  await verifyGoogleOidcJwt(fixture.token, EXPECTED, options);
  assertEquals(fetchCalls, 2);
});

Deno.test("Google OIDC claims reject a different audience", () => {
  const claims = {
    iss: "https://accounts.google.com",
    aud: "https://attacker.example",
    sub: "1234",
    email: EXPECTED.serviceAccountEmail,
    email_verified: true,
    iat: NOW - 30,
    exp: NOW + 3_000,
  };
  assertThrows(
    () => validateGoogleOidcClaims(claims, EXPECTED, NOW),
    Error,
    "invalid_google_oidc_claims",
  );
});

Deno.test("Google OIDC claims reject unverified service-account email", () => {
  const claims = {
    iss: "https://accounts.google.com",
    aud: EXPECTED.audience,
    sub: "1234",
    email: EXPECTED.serviceAccountEmail,
    email_verified: false,
    iat: NOW - 30,
    exp: NOW + 3_000,
  };
  assertThrows(
    () => validateGoogleOidcClaims(claims, EXPECTED, NOW),
    Error,
    "invalid_google_oidc_claims",
  );
});
