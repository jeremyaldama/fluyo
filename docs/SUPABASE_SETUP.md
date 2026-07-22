# Supabase Setup — Fluyo

One-time setup to provision the Supabase project used by Fluyo Android and
the separately versioned NestJS/WhatsApp integration.

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
| `service_role` secret key | Secret manager only — **never** commit. Used by the trusted external webhook/cleanup backend and hosted Edge Function. |

## 3. Apply the migrations

The migrations live at `supabase/migrations/`:

- `0001_initial_schema.sql` — tables, indexes, views, default-category trigger.
- `0002_rls_policies.sql` — RLS on all user-owned tables.
- `0003_security_hardening.sql` — security-invoker views, hardened trigger search path and revoked direct trigger execution.
- `0004_category_ondelete_setnull.sql` — permits category deletion without deleting historical expenses.
- `0005_budget_extras.sql` — extra-income records and current-month budget view/functions.
- `0006_data_integrity_and_secure_operations.sql` — expand-phase integrity constraints and trusted RPCs for deposits, gamification and verified WhatsApp linking.
- `0007_repository_closure.sql` — idempotent create/profile/archive RPCs, logical goal deletion, private receipt Storage policies, complete badge/summary semantics and bounded WhatsApp challenge retention.

Apply the seven files in `supabase/migrations/` **exactly once and in filename
order**. A database with only `0001` and `0002` is incomplete and misses later
security and product behavior.

The breaking write-path hardening is intentionally stored at
`supabase/contract-migrations/0008_write_path_contract.sql`, outside the CLI's
automatic migration directory. For an existing installation, apply through `0007`,
ship an RPC-capable Android build, verify or enforce that legacy builds no longer
write profiles, expenses, goals, extras, badges or deposits directly, repair the
new request/state fields in historical rows, and only then apply contract `0008`.
Applying expand and contract phases at once would break installed legacy clients.
On a fresh project with no clients, apply `0008` after `0007` and before distributing
the first build.

### Option A — Supabase Dashboard SQL editor

1. Project → **SQL Editor → New query**.
2. Paste and run `0001_initial_schema.sql`.
3. In a new query, paste and run `0002_rls_policies.sql`.
4. Repeat, in order, for `0003_security_hardening.sql`,
   `0004_category_ondelete_setnull.sql`, `0005_budget_extras.sql`,
   `0006_data_integrity_and_secure_operations.sql` and
   `0007_repository_closure.sql`.
5. Follow the rollout gate above, then use the explicitly confirmed contract script
   in a separate reviewed change window (immediately for a fresh project):

   ```bash
   DATABASE_URL=postgresql://... \
   CONTRACT_MIGRATION_CONFIRM='APPLY_CONTRACTS:<database>@<server-address>:<port>/<role>' \
   ./scripts/apply-contract-migrations.sh
   ```

   First run without the confirmation value to obtain the exact target-bound token.
   The script applies each file in a single transaction, records its SHA-256 under
   `fluyo_private.contract_migrations`, and rechecks constraints, policies and ACLs;
   do not replace it with an unrecorded SQL-editor paste.
6. Verify under **Database → Tables** that `users`, `categories`, `expenses`,
   `goals`, `badges`, `goal_deposits`, `budget_extras`, `whatsapp_links` and
   `whatsapp_link_challenges` exist, and that RLS is enabled on every user-owned
   or identity-link table.

### Option B — Supabase CLI

```bash
brew install supabase/tap/supabase
supabase login
supabase init # only when supabase/config.toml does not exist yet
supabase link --project-ref <YOUR_PROJECT_REF>
supabase db push --dry-run
supabase db push
```

Review the dry-run list before applying it. The CLI records applied migrations in
its migration history; do not also paste the same migration manually afterward.
The CLI intentionally does **not** see contract `0008`. Apply it through
`scripts/apply-contract-migrations.sh` only after satisfying the rollout gate; retain
the printed registry evidence in the deployment log.

### Reproduce both migration histories locally

Point the harness only at a new, empty, disposable database. It independently tests a
fresh install and an upgrade from `0001..0005` with representative legacy rows, repair,
constraint validation, contract registration, RPC/RLS/Storage behavior and a real
two-session currency race:

```bash
DATABASE_URL=postgresql://.../fluyo_migration_test \
MIGRATION_TEST_CONFIRM_RESET=fluyo_migration_test \
./scripts/test-migrations.sh
```

The second variable must equal PostgreSQL's exact `current_database()` value. The
harness refuses system databases, any pre-existing application schema/object/extension,
and unconfirmed resets. After accepting an empty target it writes a per-run marker bound
to the exact database, server address, port, role and random nonce; the destructive reset
between scenarios proceeds only while that complete identity still matches.

### Validate staged constraints on an existing database

`0006` and contract `0008` add constraints as `NOT VALID` so deployment does not delete or
silently rewrite historical rows. PostgreSQL still enforces them for new/changed
rows, but old rows are not proven valid until an operator completes this gate:

1. Take a recoverable backup and run the migration against staging first.
2. Inventory every pending definition:

```sql
select c.conrelid::regclass as table_name,
       c.conname,
       pg_get_constraintdef(c.oid)
from pg_catalog.pg_constraint as c
join pg_catalog.pg_class as t on t.oid = c.conrelid
join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
where n.nspname = 'public' and not c.convalidated
order by 1, 2;
```

3. For each definition, query and review violating rows, then repair them through
   a reviewed, auditable backfill. Do not delete or coerce financial history merely
   to make validation pass.
4. Validate constraints individually in a controlled window:

```sql
alter table public.<table> validate constraint <constraint_name>;
```

5. Re-run the inventory until it returns zero rows and record the evidence per
   environment. `supabase/tests/validate_constraints.sql` automates this only for
   the disposable fresh/fixture-upgrade databases used by CI; it is deliberately
   not a production repair script.

## 4. Enable auth providers

**Project Settings → Authentication → Providers:**

- **Email**: enabled (default). Disable "Confirm email" for development if you
  want quick signups; re-enable for production.
- **Google**: enabled. Requires a Google Cloud OAuth client — see
  `docs/GOOGLE_OAUTH_SETUP.md` for the full procedure.

After completing Google setup, paste the **Web OAuth client ID** and
**client secret** into Supabase's Google provider config, then save.

Under **Authentication → URL Configuration → Redirect URLs**, allow the exact
mobile callback `fluyo://auth-callback`. Do not use a wildcard callback. Test both
email confirmation and OAuth on a release-signed staging build before production.

The custom scheme is strictly validated and scrubbed by the app, but Android cannot
prove ownership of a private scheme. Before a production launch, provision a controlled
HTTPS domain, publish the matching `assetlinks.json`, migrate the manifest/Auth redirect
to a verified App Link and replace this allow-list entry. That domain and certificate
are external deployment inputs and are not invented by this repository.

## 5. Provision the receipts bucket

Migration `0007_repository_closure.sql` owns this configuration; do not create
different dashboard-only policies. On hosted Supabase it upserts a private `receipts`
bucket with a 10 MiB limit, permits JPEG/PNG/WebP, and installs versioned policies:

- active users may insert/update only `receipts/<auth.uid()>/*`;
- authenticated users may read/delete only their own prefix;
- an account tombstone blocks new uploads before cleanup while preserving delete access.

Verify the versioned state after migration:

```sql
select id, public, file_size_limit, allowed_mime_types
from storage.buckets
where id = 'receipts';

select policyname, cmd
from pg_policies
where schemaname = 'storage' and tablename = 'objects'
  and policyname like 'fluyo_receipts_%'
order by policyname;
```

The migration harness exercises the same metadata/policies against a compatible
local Storage stand-in, including upload rejection after account deletion begins.

## 6. Deploy trusted server operations

### Account deletion Edge Function

The Android client calls `delete-account`; a release is incomplete until this
function and its external cleanup dependency are deployed.

1. Implement an authenticated, idempotent HTTPS cleanup endpoint in the external
   WhatsApp backend. It must first atomically mark `auth_user_id` and every verified
   sender identity as `deleting`, then reject new webhooks/uploads for that identity,
   and only afterward drain or irreversibly detach all audio/media and other artifacts.
   Return a non-2xx status if the tombstone or cleanup is incomplete. Without this
   ordering, a concurrent webhook can create orphaned media between cleanup and Auth deletion.
2. Generate a dedicated high-entropy bearer secret in the deployment secret
   manager. Do not reuse a Supabase key and do not paste it into this repository.
3. Configure and deploy:

```bash
supabase secrets set \
  ACCOUNT_DELETION_CLEANUP_URL=https://<backend>/internal/fluyo/account-cleanup \
  ACCOUNT_DELETION_CLEANUP_SECRET=<DEDICATED_RANDOM_SECRET>
supabase functions deploy delete-account
```

Supabase supplies `SUPABASE_URL`, `SUPABASE_ANON_KEY` and
`SUPABASE_SERVICE_ROLE_KEY` to its hosted Edge Function runtime. The function
fails closed if the external cleanup hook is missing, non-HTTPS, times out or
returns an error; verify those failure cases in staging as well as a successful
deletion of Auth, public rows, receipts and external media.

The tombstone/write-rejection behavior belongs to the external backend and cannot
be verified by this repository's CI. Treat its implementation and deployment as a
production launch gate, not as behavior supplied by the Edge Function alone.

### Verified WhatsApp linking

Do not enable the WhatsApp entry point until the external webhook backend:

1. validates Meta's webhook authenticity before trusting the sender;
2. canonicalizes that authenticated sender to E.164;
3. extracts the one-time token sent by the user;
4. invokes `confirm_whatsapp_link_challenge(token, phone_e164)` using the
   server-only `service_role` credential; and
5. resolves all later writes exclusively through `whatsapp_links.phone_e164`.

The Android entry point is fail-closed. It stays hidden unless the build receives both:

```properties
WHATSAPP_LINKING_ENABLED=true
WHATSAPP_BOT_NUMBER=<E.164 digits without +>
```

Keep `WHATSAPP_LINKING_ENABLED=false` (the default) until all five controls above pass
in the target environment. Release validation rejects an enabled flow with a missing or
malformed destination number.

The mobile app may call `create_whatsapp_link_challenge()` and place the returned
token in a WhatsApp message. A typed `users.phone_number` is legacy data and must
never authorize a financial write.

Migration `0007` keeps the short terminal audit window bounded with two disjoint,
index-backed retention paths (active rows by `expires_at`, terminal rows by
`created_at`). Do not merge them into one `DELETE ... OR ...`: that turns every
challenge creation into a global table scan as traffic grows. The migration harness
checks both partial indexes, cross-user cleanup and idempotent replay.

## 7. Smoke test from the dashboard

Run this in the SQL editor — it should return zero rows. The explicit transaction is
required because `SET LOCAL` outside a transaction is immediately discarded:

```sql
begin;
set local role authenticated;
select set_config(
    'request.jwt.claim.sub',
    '00000000-0000-0000-0000-000000000000',
    true
);
select * from public.users;
rollback;
```

The rollback restores both the role and synthetic JWT claim.

## 8. After Android builds, verify end-to-end

After running the app and signing in once:

```sql
-- Should show 1 row in auth.users + 1 row in public.users + 7 rows in categories.
select count(*) from auth.users;
select count(*) from public.users;
select count(*) from public.categories;

-- Onboarding writes should land in public.users. phone_number is legacy only.
select monthly_budget, currency from public.users limit 1;

-- Added by 0005; should execute without "relation does not exist".
select * from public.current_month_budget limit 1;

-- Added by 0006; authenticated users can only read their own verified link.
select * from public.whatsapp_links limit 1;
```

Also execute the repository smoke script against a non-production project and
manually verify sign-up confirmation, user switching, a retried goal deposit,
WhatsApp challenge expiry, CSV export, and account deletion. Never run destructive
deletion tests against a real user's production account.
