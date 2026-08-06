# Gmail receipt auto-import — setup guide

This feature registers expenses automatically when a payment receipt (Yape, bank
notification, etc.) lands in the user's Gmail inbox. It uses **Gmail push
notifications via Google Cloud Pub/Sub** → a **Supabase Edge Function** that
reads the email, parses the amount, and inserts the expense.

This document covers the one-time infrastructure setup. The code is already in
the repo:

- `supabase/migrations/0006_add_email_source.sql` — DB schema (`expenses.source = 'email'`, `email_grants` table).
- `supabase/functions/gmail-webhook/` — receives Pub/Sub push, fetches + parses mail, inserts expenses.
- `supabase/functions/gmail-connect/` — OAuth flow (user links their Gmail).
- `supabase/functions/_shared/` — shared parser, DB client, Gmail API client.

---

## Prerequisites

- A Supabase project with the Fluyo migrations applied (0001–0006).
- A Google Cloud project (can be the same one that holds your OAuth Web Client
  for sign-in, or a new one).
- The Supabase CLI installed (`npm i -g supabase`) to deploy Edge Functions.

---

## 1. Google Cloud setup

### 1.1 Enable the Gmail API

1. Go to **Google Cloud Console** → select your project.
2. **APIs & Services → Library** → search **Gmail API** → **Enable**.

### 1.2 Create OAuth credentials for Gmail (separate from sign-in)

The `GOOGLE_WEB_CLIENT_ID` already in `local.properties` is for **Supabase
Auth sign-in**. The email feature needs a *different* OAuth client with the
`gmail.readonly` scope.

1. **APIs & Services → Credentials → Create credentials → OAuth client ID**.
2. Application type: **Web application**.
3. Authorized redirect URI:
   `https://<your-supabase-project>.functions.supabase.co/functions/v1/gmail-connect`
   (replace `<your-supabase-project>` — for this project, `fxbrxfsyxmzadyonhaoj`).
4. Note the **Client ID** and **Client Secret**.

### 1.3 Configure the OAuth consent screen

`gmail.readonly` is a **restricted scope**. For development:

1. **APIs & Services → OAuth consent screen**.
2. Set it to **Testing** (not Production — production requires Google's
   verification process, which can take weeks).
3. Add your test users' Gmail addresses under **Test users**.
4. Add the scope `https://www.googleapis.com/auth/gmail.readonly`.

> **For public production launch**, you must submit the app for Google's OAuth
> verification. Budget 2–6 weeks and a security assessment. This is the single
> biggest external dependency of the feature.

### 1.4 Create the Pub/Sub topic + subscription

Gmail pushes mailbox changes to Pub/Sub; Pub/Sub forwards them to our Edge
Function.

1. **Pub/Sub → Topics → Create topic**, name it `gmail-receipts`.
2. Grant publish rights: **topic → Permissions → Add member** →
   `gmail-api-push@system.gserviceaccount.com` with role **Pub/Sub Publisher**.
   (Without this, Gmail cannot publish.)
3. **Create subscription** for the topic:
   - Delivery type: **Push**.
   - Endpoint URL: `https://<your-supabase-project>.functions.supabase.co/functions/v1/gmail-webhook`
   - Ack deadline: 10s (the webhook ACKs immediately).

---

## 2. Supabase setup

### 2.1 Apply the migration

```bash
supabase db push
# or apply 0006_add_email_source.sql directly via the SQL editor
```

This adds the `email` value to `expenses.source` and creates the `email_grants`
table with RLS.

### 2.2 Enable the Vault extension (for storing refresh tokens)

The Google OAuth refresh token is stored encrypted in Supabase Vault, never in
plaintext. Vault is on by default in recent Supabase versions; verify in the
Dashboard → **Database → Extensions** → `vault` should be enabled.

> The `gmail-connect` function references `vault/secrets` and the
> `decrypted_secret` RPC. If you see a Vault error, run
> `create extension if not exists vault;` in the SQL editor.

### 2.3 Set Edge Function secrets

In the Dashboard → **Project Settings → Edge Functions → Secrets**, add:

| Secret name | Value |
|---|---|
| `GMAIL_CLIENT_ID` | (from step 1.2) |
| `GMAIL_CLIENT_SECRET` | (from step 1.2) |
| `GOOGLE_PUBSUB_TOPIC` | `projects/<gcp-project-id>/topics/gmail-receipts` |

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically by
Supabase for Edge Functions — no need to set them.

### 2.4 Deploy the Edge Functions

```bash
supabase functions deploy gmail-webhook --no-verify-jwt
supabase functions deploy gmail-connect
```

> `gmail-webhook` is deployed with `--no-verify-jwt` because Pub/Sub calls it
> with its own auth, not a Supabase user JWT. The function validates the Pub/Sub
> payload structure instead.
>
> `gmail-connect` keeps JWT verification **off** at the edge level too, because
> the JWT travels in the `state` param through Google's redirect — it's checked
> inside the function, not by Supabase's gateway.

---

## 3. How a user links their Gmail (runtime flow)

1. User opens **Profile → Importar boletas (Gmail)** and taps the row.
2. The app opens the browser to `gmail-connect?token=<JWT>`.
3. `gmail-connect` redirects to Google's consent screen (`gmail.readonly`).
4. User consents → Google redirects back to `gmail-connect?code=...`.
5. `gmail-connect` exchanges the code for a **refresh token**, stores it in
   Vault, inserts a row in `email_grants`, and calls `gmail.users.watch()`.
6. From now on, Gmail publishes to Pub/Sub on every inbox change → Pub/Sub
   pushes to `gmail-webhook` → the function reads the new mail, parses
   whitelisted receipts, and inserts expenses with `source = 'email'`.

---

## 4. Which senders get parsed

Only emails from whitelisted senders become expenses — everything else is
ignored (privacy: we never store non-receipt mail). The whitelist lives in
`supabase/functions/_shared/receipt-parser.ts` (`SENDER_WHITELIST`). Add
institutions there after verifying their exact sender domain against a real
notification.

Current whitelist: Yape, BCP (`@viabcp.com`), Interbank, BBVA, Scotiabank.

---

## 5. Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| No expenses appear after linking | Refresh token not stored, or `watch()` failed | Check `gmail-connect` logs; re-link the account |
| Webhook returns 200 but nothing inserts | Sender not whitelisted, or amount not parseable | Check `gmail-webhook` logs for the message id; add sender to whitelist or improve regex |
| `vault` errors on connect | Vault extension not enabled | `create extension if not exists vault;` |
| Pub/Sub not delivering | `gmail-api-push@…` lacks Publisher role | Re-check topic IAM (step 1.4) |
| OAuth shows "access blocked" | Consent screen in Testing, user not in test list | Add the user under Test users (step 1.3) |

---

## 6. Known limitations / future work

- **Regex parsing is fragile.** Different banks format receipts differently.
  The v2 should replace `receipt-parser.ts` with an LLM call (OpenAI, same
  account the WhatsApp bot uses) for robust extraction. The module is isolated
  so this swap touches nothing else.
- **Outlook/Microsoft Graph is not supported.** The architecture leaves room
  for an `outlook-webhook/` later; Graph uses a different subscription model.
- **No dedupe yet.** If a webhook retries mid-batch, the same message could be
  inserted twice. The cursor (`email_grants.history_id`) advances only after
  the batch, but a hard crash could replay. Add a `message_id` uniqueness
  column to `expenses` (or a dedupe table) in v2.
