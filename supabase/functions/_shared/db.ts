// Database helpers for the email-import Edge Functions.
//
// Same trust model as the WhatsApp backend plugin (SYSTEM_DESIGN.md §6.3):
// these functions run with the Supabase SERVICE-ROLE key, which bypasses RLS,
// because the push webhook has no user JWT — the user identity is the Gmail
// address cross-referenced against email_grants.email.
//
// We use the Postgres connection directly via Supabase's PostgREST (the REST
// API), keeping this dependency-free (no @supabase/supabase-js import needed).

const SUPABASE_URL = Deno.env.get("SUPABASE_URL") ?? "";
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

if (!SUPABASE_URL || !SERVICE_ROLE_KEY) {
  // Fail loud at module load if the secrets aren't set. The function will 500,
  // which is correct — there is nothing useful it can do without these.
  console.error("FATAL: SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY not set");
}

export interface EmailGrantRow {
  id: string;
  user_id: string;
  email: string;
  google_refresh_token_secret_id: string | null;
  history_id: string | null;
}

/**
 * Look up the grant for a given Gmail address. Returns null if the user has not
 * linked this address (in which case the webhook drops the notification — we
 * never write expenses for unlinked mailboxes).
 */
export async function getGrantByEmail(email: string): Promise<EmailGrantRow | null> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/email_grants?email=eq.${encodeURIComponent(email)}&select=id,user_id,email,google_refresh_token_secret_id,history_id`,
    {
      headers: {
        apikey: SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
        Accept: "application/vnd.pgrst.object+json", // single row or 406
      },
    },
  );
  if (res.status === 406) return null; // no row
  if (!res.ok) {
    throw new Error(`email_grants lookup failed: ${res.status} ${await res.text()}`);
  }
  return res.json();
}

/**
 * Persist the new Gmail historyId cursor after a successful sync, so the next
 * webhook only fetches messages newer than this point.
 */
export async function updateGrantHistoryId(grantId: string, historyId: string): Promise<void> {
  const res = await fetch(
    `${SUPABASE_URL}/rest/v1/email_grants?id=eq.${grantId}`,
    {
      method: "PATCH",
      headers: {
        apikey: SERVICE_ROLE_KEY,
        Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
        "Content-Type": "application/json",
        Prefer: "return=minimal",
      },
      body: JSON.stringify({ history_id: historyId }),
    },
  );
  if (!res.ok) {
    throw new Error(`history_id update failed: ${res.status} ${await res.text()}`);
  }
}

export interface InsertExpenseInput {
  user_id: string;
  amount: number;
  description: string | null;
  expense_date: string | null; // ISO date or null → server defaults to today
  source: "email";
  recipient: string | null;
}

/**
 * Insert an email-sourced expense on behalf of a user. Uses the service-role
 * key, bypassing RLS — same mechanism the WhatsApp plugin uses to write
 * `source='whatsapp'` rows for users identified by phone number.
 */
export async function insertExpense(input: InsertExpenseInput): Promise<void> {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/expenses`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
      Prefer: "return=minimal",
    },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(`expense insert failed: ${res.status} ${await res.text()}`);
  }
}

/**
 * Recover the plaintext Google OAuth refresh token from Supabase Vault by its
 * secret id. The token was stored encrypted at grant time; only the service
 * role can read the decrypted value.
 */
export async function getDecryptedVaultSecret(secretId: string): Promise<string> {
  // vault.decrypted_secret(id) is a SQL function exposed over PostgREST via RPC.
  const res = await fetch(`${SUPABASE_URL}/rest/v1/rpc/decrypted_secret`, {
    method: "POST",
    headers: {
      apikey: SERVICE_ROLE_KEY,
      Authorization: `Bearer ${SERVICE_ROLE_KEY}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ id: secretId }),
  });
  if (!res.ok) {
    throw new Error(`vault decrypt failed: ${res.status} ${await res.text()}`);
  }
  // The function returns the secret as text.
  return res.text();
}
