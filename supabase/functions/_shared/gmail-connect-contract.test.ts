import { assertEquals, assertRejects, assertThrows } from "jsr:@std/assert@1";
import {
  buildOAuthCompletionRedirect,
  GmailConnectContractError,
  parseGmailConnectRequest,
  resolvePublicOAuthCallback,
  runForOAuthStateOwner,
} from "./gmail-connect-contract.ts";

const STATE = `v1.${"a".repeat(16)}.${"b".repeat(80)}`;

Deno.test("Gmail connect POST contract accepts explicit init, legacy init and completion", () => {
  assertEquals(
    parseGmailConnectRequest({ action: "init", redirect_uri: "com.qolve.fluyo://gmail-callback" }),
    { kind: "init", redirectUri: "com.qolve.fluyo://gmail-callback" },
  );
  assertEquals(parseGmailConnectRequest({ redirect_uri: "com.qolve.fluyo://gmail-callback" }), {
    kind: "init",
    redirectUri: "com.qolve.fluyo://gmail-callback",
  });
  assertEquals(parseGmailConnectRequest({ action: "complete", authorization_code: "4/abc-DEF_123", state: STATE }), {
    kind: "complete",
    authorizationCode: "4/abc-DEF_123",
    state: STATE,
  });
  assertEquals(
    parseGmailConnectRequest({ action: "complete", authorization_code: "opaque~+%=:@?code", state: STATE }),
    { kind: "complete", authorizationCode: "opaque~+%=:@?code", state: STATE },
  );
});

Deno.test("Gmail connect POST contract rejects mixed, unknown and malformed fields", () => {
  for (
    const body of [
      { action: "complete", authorization_code: "4/abc", state: STATE, redirect_uri: "https://evil.example" },
      { action: "complete", authorization_code: "bad code", state: STATE },
      { action: "complete", authorization_code: "a".repeat(4_097), state: STATE },
      { action: "complete", authorization_code: "bad\ncode", state: STATE },
      { action: "complete", authorization_code: "4/abc", state: "not-sealed" },
      { authorization_code: "4/abc", state: STATE },
      { action: "init" },
      { action: "init", redirect_uri: "" },
      { action: "unknown", redirect_uri: "com.qolve.fluyo://gmail-callback" },
    ]
  ) {
    const error = assertThrows(() => parseGmailConnectRequest(body), GmailConnectContractError);
    assertEquals(error.code, "invalid_request");
  }
});

Deno.test("OAuth state owner mismatch cannot run the code-consuming action", async () => {
  let proceeded = false;
  const error = await assertRejects(
    () =>
      runForOAuthStateOwner("user-a", "user-b", () => {
        proceeded = true;
        return Promise.resolve();
      }),
    GmailConnectContractError,
  );
  assertEquals(error.code, "state_user_mismatch");
  assertEquals(error.status, 403);
  assertEquals(proceeded, false);
});

Deno.test("OAuth state owner match is the only path that runs completion", async () => {
  let proceeded = false;
  const result = await runForOAuthStateOwner("user-a", "user-a", () => {
    proceeded = true;
    return Promise.resolve("completed");
  });
  assertEquals(proceeded, true);
  assertEquals(result, "completed");
});

Deno.test("public callback completion redirect contains only code, encrypted state and status", () => {
  const redirect = new URL(
    buildOAuthCompletionRedirect("com.qolve.fluyo://gmail-callback?stale=value#fragment", "4/abc-DEF_123", STATE),
  );
  assertEquals([...redirect.searchParams.keys()].sort(), ["code", "state", "status"]);
  assertEquals(redirect.searchParams.get("status"), "complete");
  assertEquals(redirect.searchParams.get("code"), "4/abc-DEF_123");
  assertEquals(redirect.searchParams.get("state"), STATE);
  assertEquals(redirect.searchParams.has("email"), false);
  assertEquals(redirect.searchParams.has("token"), false);
  assertEquals(redirect.hash, "");
});

Deno.test("public callback resolver can only open state and returns completion data without persistence fields", async () => {
  let stateOpened = 0;
  const outcome = await resolvePublicOAuthCallback(
    new URLSearchParams({ code: "4/abc-DEF_123", state: STATE }),
    "com.qolve.fluyo://gmail-callback",
    (state) => {
      stateOpened++;
      assertEquals(state, STATE);
      return Promise.resolve({ redirectUri: "com.qolve.fluyo://gmail-callback" });
    },
  );
  assertEquals(stateOpened, 1);
  assertEquals(outcome, {
    kind: "complete",
    redirectUri: "com.qolve.fluyo://gmail-callback",
    authorizationCode: "4/abc-DEF_123",
    state: STATE,
  });
  assertEquals("email" in outcome, false);
  assertEquals("userId" in outcome, false);
  assertEquals("refreshToken" in outcome, false);
});
