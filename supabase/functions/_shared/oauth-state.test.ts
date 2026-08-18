import { assertEquals, assertRejects } from "jsr:@std/assert@1";
import { createPkcePair, openOAuthState, sealOAuthState } from "./oauth-state.ts";

const SECRET = "test-only-secret-that-is-more-than-thirty-two-bytes";
const USER_ID = "123e4567-e89b-42d3-a456-426614174000";

Deno.test("OAuth state round-trips without exposing identity or verifier", async () => {
  const pkce = await createPkcePair();
  const state = await sealOAuthState(SECRET, {
    userId: USER_ID,
    redirectUri: "com.qolve.fluyo://gmail-callback",
    codeVerifier: pkce.verifier,
  }, 1_700_000_000);

  assertEquals(state.includes(USER_ID), false);
  assertEquals(state.includes(pkce.verifier), false);
  const decoded = await openOAuthState(SECRET, state, 1_700_000_300);
  assertEquals(decoded.userId, USER_ID);
  assertEquals(decoded.codeVerifier, pkce.verifier);
  assertEquals(pkce.verifier.length, 43);
  assertEquals(pkce.challenge.length, 43);
});

Deno.test("OAuth state rejects tampering", async () => {
  const pkce = await createPkcePair();
  const state = await sealOAuthState(SECRET, {
    userId: USER_ID,
    redirectUri: "com.qolve.fluyo://gmail-callback",
    codeVerifier: pkce.verifier,
  }, 1_700_000_000);
  const last = state.at(-1)!;
  const tampered = `${state.slice(0, -1)}${last === "A" ? "B" : "A"}`;
  await assertRejects(() => openOAuthState(SECRET, tampered, 1_700_000_001), Error, "invalid_state");
});

Deno.test("OAuth state expires after ten minutes", async () => {
  const pkce = await createPkcePair();
  const state = await sealOAuthState(SECRET, {
    userId: USER_ID,
    redirectUri: "com.qolve.fluyo://gmail-callback",
    codeVerifier: pkce.verifier,
  }, 1_700_000_000);
  await assertRejects(() => openOAuthState(SECRET, state, 1_700_000_601), Error, "expired_state");
});
