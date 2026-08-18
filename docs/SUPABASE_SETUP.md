# Supabase Setup — Fluyo

One-time setup to provision the Supabase project that Fluyo (Android) and
the future Fluyo NestJS plugin both talk to.

## 1. Create the project

1. Sign in at https://supabase.com/dashboard.
2. **New project** → name `fluyo-prod` → region **`sa-east-1` (São Paulo)**
   (closest to Lima). Pick a strong database password and stash it in 1Password
   under "Fluyo Supabase Postgres".
3. Wait ~2 minutes for the project to provision.

## 2. Capture credentials

From **Project Settings → API**, copy:

| Value | Where it goes |
|-------|---------------|
| `Project URL` | `local.properties` → `SUPABASE_URL` |
| `anon` public key | `local.properties` → `SUPABASE_ANON_KEY` |
| `service_role` secret key | 1Password only — **never** commit. Used by the future NestJS plugin. |

## 3. Apply the migrations

The migrations live at `supabase/migrations/`:

- `0001_initial_schema.sql` — tables, indexes, views, default-category trigger.
- `0002_rls_policies.sql` — RLS on all user-owned tables.
- `0003_security_hardening.sql` — hardened functions, grants and constraints.
- `0004_category_ondelete_setnull.sql` — safe category deletion for existing expenses.
- `0005_budget_extras.sql` — budget configuration fields.
- `0006_add_email_source.sql` — Gmail expense source and grant table.
- `0007_harden_email_ingestion.sql` — Vault RPCs, watch metadata, dedupe and atomic Gmail ingestion.

### Option A — Supabase Dashboard SQL editor

1. Project → **SQL Editor → New query**.
2. Run every migration from `0001` through `0007`, in filename order, using a
   new query for each file.
3. Verify under **Database → Tables**: `users`, `categories`, `expenses`,
   `goals`, `badges`, `goal_deposits` all exist with RLS enabled.

For normal environments prefer the CLI option below: it records migration
history and avoids accidentally skipping a file.

If you use SQL Editor, do not switch later to `supabase db push` until the
remote migration history has been reconciled with `supabase migration repair`;
otherwise the CLI may try to execute already-applied files again.

### Option B — Supabase CLI

```bash
brew install supabase/tap/supabase
supabase login
supabase link --project-ref <YOUR_PROJECT_REF>
supabase db push
```

The Gmail infrastructure and function deployment are separate steps; follow
`docs/GMAIL_PUSH_SETUP.md` after the schema is current.

## 4. Enable auth providers

**Project Settings → Authentication → Providers:**

- **Email**: enabled (default). Disable "Confirm email" for development if you
  want quick signups; re-enable for production.
- **Google**: enabled. Requires a Google Cloud OAuth client — see
  `docs/GOOGLE_OAUTH_SETUP.md` for the full procedure.

After completing Google setup, paste the **Web OAuth client ID** and
**client secret** into Supabase's Google provider config, then save.

## 5. Provision the receipts bucket

**Project → Storage → New bucket**: `receipts`, **Private**.

Storage policies (under the bucket's Policies tab) — use the dashboard's
"For authenticated users only" template, then narrow to a user-prefix path:

```sql
-- INSERT: users can upload to receipts/<auth.uid()>/*
create policy "receipts_upload_own"
on storage.objects for insert to authenticated
with check (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
);

-- SELECT: users can read their own files
create policy "receipts_read_own"
on storage.objects for select to authenticated
using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
);

-- DELETE: users can delete their own files
create policy "receipts_delete_own"
on storage.objects for delete to authenticated
using (
    bucket_id = 'receipts'
    and (storage.foldername(name))[1] = auth.uid()::text
);
```

## 6. Smoke test from the dashboard

Run this in the SQL editor — it should return zero rows (RLS prevents
cross-user reads even from the editor when `set role` is anon):

```sql
set local role authenticated;
set local request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000"}';
select * from public.users;
```

Reset with `reset role; reset request.jwt.claims;`.

## 7. After Android builds, verify end-to-end

After running the app and signing in once:

```sql
-- Should show 1 row in auth.users + 1 row in public.users + 7 rows in categories.
select count(*) from auth.users;
select count(*) from public.users;
select count(*) from public.categories;

-- Onboarding writes should land in public.users
select monthly_budget, phone_number from public.users limit 1;
```
