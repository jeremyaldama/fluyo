// gmail-connect — OAuth flow to link a user's Gmail for receipt ingestion.
//
// Two phases, selected by whether ?code= is present:
//
//   1. INIT  (no code): the Android app opens this URL in a Custom Tab with the
//      user's Supabase JWT in `state`. We redirect to Google's consent screen.
//      access_type=offline + prompt=consent forces Google to return a refresh
//      token (without prompt=consent, Google only issues a refresh token the
//      FIRST time a user consents, which breaks re-linking).
//
//   2. CALLBACK (code present): Google redirects back here with ?code=. We:
//        a. exchange code → { access_token, refresh_token, id_token }
//        b. read the Gmail address from the id_token (we don't trust the JWT
//           in `state` at this point — it's been a round-trip through Google)
//        c. resolve user_id from the `state` JWT (the app's Supabase user)
//        d. store the refresh token in Vault, upsert email_grants
//        e. call gmail.users.watch() to start push notifications
//        f. redirect back to the app via deep link (com.qolve.fluyo://gmail-callback)

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";
const GMAIL_CLIENT_ID = Deno.env.get("GMAIL_CLIENT_ID") ?? "";
const GMAIL_CLIENT_SECRET = Deno.env.get("GMAIL_CLIENT_SECRET") ?? "";
const PUBSUB_TOPIC = Deno.env.get("GOOGLE_PUBSUB_TOPIC") ?? "projects/fluyo/topics/gmail-receipts";

const AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth";
const TOKEN_URL = "https://oauth2.googleapis.com/token";
const SCOPE = "https://www.googleapis.com/auth/gmail.readonly";
// The function's own URL is the redirect target (Google calls us back here).
const REDIRECT_PATH = "/functions/v1/gmail-connect";

const DEEP_LINK_SUCCESS = "com.qolve.fluyo://gmail-callback?success=1";
const DEEP_LINK_ERROR = "com.qolve.fluyo://gmail-callback?success=0";

/** Extract the function's public base URL from the request. */
function selfUrl(req: Request): string {
  const url = new URL(req.url);
  // In production the host is <project>.functions.supabase.co; locally
  // (supabase functions serve) it's localhost:54321. Either way req.url is right.
  return `${url.protocol}//${url.host}${REDIRECT_PATH}`;
}

/** Decode a JWT payload without verifying (we only read the sub/email). */
function decodeJwtPayload(token: string): Record<string, unknown> {
  try {
    const payload = token.split(".")[1];
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json);
  } catch {
    return {};
  }
}

Deno.serve(async (req: Request) => {
  const url = new URL(req.url);

  // -------- PHASE 2: CALLBACK (Google redirected back with ?code=) --------
  if (url.searchParams.has("code")) {
    return handleCallback(req, url);
  }

  // -------- PHASE 1: INIT (redirect to Google consent) --------
  const token = url.searchParams.get("token"); // Supabase access JWT from the app
  if (!token) {
    return new Response("missing token", { status: 400 });
  }
  const consentUrl = new URL(AUTH_URL);
  consentUrl.searchParams.set("client_id", GMAIL_CLIENT_ID);
  consentUrl.searchParams.set("redirect_uri", selfUrl(req));
  consentUrl.searchParams.set("response_type", "code");
  consentUrl.searchParams.set("scope", SCOPE);
  consentUrl.searchParams.set("access_type", "offline");
  consentUrl.searchParams.set("prompt", "consent"); // force refresh_token issuance
  consentUrl.searchParams.set("state", token); // survive the round-trip
  return Response.redirect(consentUrl.toString(), 302);
});

async function handleCallback(req: Request, url: URL): Promise<Response> {
  const code = url.searchParams.get("code")!;
  const stateJwt = url.searchParams.get("state") ?? "";
  try {
    // a. Exchange authorization code for tokens.
    const tokenRes = await fetch(TOKEN_URL, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        code,
        client_id: GMAIL_CLIENT_ID,
        client_secret: GMAIL_CLIENT_SECRET,
        redirect_uri: selfUrl(req),
        grant_type: "authorization_code",
      }),
    });
    if (!tokenRes.ok) throw new Error(`token exchange failed: ${await tokenRes.text()}`);
    const tokens = await tokenRes.json();
    const refreshToken = tokens.refresh_token as string | undefined;
    if (!refreshToken) {
      // Happens if user re-consents without prompt=consent. We force prompt, so
      // this is unexpected — surface it rather than store a grant we can't sync.
      throw new Error("no refresh_token in response");
    }

    // b. Read the Gmail address from the id_token.
    const idToken = tokens.id_token as string | undefined;
    const claims = idToken ? decodeJwtPayload(idToken) : {};
    const gmailAddress = claims.email as string | undefined;
    if (!gmailAddress) throw new Error("no email in id_token");

    // c. Resolve the Fluyo user_id from the state JWT (the app's Supabase user).
    const stateClaims = decodeJwtPayload(stateJwt);
    const authId = stateClaims.sub as string | undefined;
    if (!authId) throw new Error("no sub in state JWT");
    const userId = await resolveUserIdByAuthId(authId);

    // d. Store refresh token in Vault, upsert email_grants.
    const secretId = await createVaultSecret(refreshToken, `gmail-refresh:${gmailAddress}`);
    await upsertGrant(userId, gmailAddress, secretId);

    // e. Start push notifications.
    await startWatch(tokens.access_token, PUBSUB_TOPIC);

    // f. Back to the app.
    return Response.redirect(DEEP_LINK_SUCCESS, 302);
  } catch (err) {
    console.error("gmail-connect callback error:", err instanceof Error ? err.message : err);
    return Response.redirect(DEEP_LINK_ERROR, 302);
  }
}

/** Look up public.users.id from auth.users.id (the JWT sub). */
async function resolveUserIdByAuthId(authId: string): Promise<string> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/users?auth_id=eq.${authId}&select=id`,
    {
      headers: {
        apikey: SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
        Accept: "application/vnd.pgrst.object+json",
      },
    },
  );
  if (!res.ok) throw new Error(`user lookup failed: ${res.status} ${await res.text()}`);
  const row = await res.json();
  return row.id;
}

/** Store a secret in Vault, returning the secret id to reference in email_grants. */
async function createVaultSecret(secret: string, name: string): Promise<string> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/vault/secrets`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "return=representation",
    },
    body: JSON.stringify({ secret, name }),
  });
  if (!res.ok) throw new Error(`vault create failed: ${res.status} ${await res.text()}`);
  const row = await res.json();
  return row[0].id;
}

/** Insert or update the grant for this user+email. */
async function upsertGrant(userId: string, email: string, secretId: string): Promise<void> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/email_grants?user_id=eq.${userId}&email=eq.${encodeURIComponent(email)}`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal,resolution=merge-duplicates", // upsert
    },
    body: JSON.stringify({
      user_id: userId,
      email,
      google_refresh_token_secret_id: secretId,
    }),
  });
  if (!res.ok) throw new Error(`grant upsert failed: ${res.status} ${await res.text()}`);
}

/** Register the mailbox for push notifications to our Pub/Sub topic. */
async function startWatch(accessToken: string, topic: string): Promise<void> {
  const res = await fetch("https://gmail.googleapis.com/gmail/v1/users/me/watch", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${accessToken}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      topicName: topic,
      labelIds: ["INBOX"],
      labelFilterBehavior: "include",
    }),
  });
  if (!res.ok) {
    // Non-fatal: the grant is saved; we'll retry watch on the first webhook or
    // the user can re-link. Log but don't fail the whole connect.
    console.error(`watch() failed (non-fatal): ${res.status} ${await res.text()}`);
  }
}
