-- Fluyo — email receipt ingestion support
-- 1) Extends expenses.source to include 'email' (auto-imported from Gmail boletas).
-- 2) Adds the email_grants table: stores the OAuth grant a user gives us to read
--    receipt emails, plus the Gmail historyId cursor for push incremental sync.
--
-- SECURITY: the Google OAuth refresh token is a long-lived credential. It is NOT
-- stored in plaintext here. We store only a Vault secret id (vault.secret_id())
-- in email_grants.google_refresh_token_secret_id; the Edge Function recovers the
-- token via `vault.decrypted_secret(<id>)`. The Vault extension must be enabled
-- on the project (it is on by default on Supabase >= 1.50). See
-- docs/GMAIL_PUSH_SETUP.md.

-- ============================================================
-- 1) Extend expenses.source CHECK to include 'email'
-- ============================================================
-- Postgres auto-names a column CHECK constraint "<table>_<column>_check".
alter table public.expenses
    drop constraint if exists expenses_source_check;

alter table public.expenses
    add constraint expenses_source_check
    check (source in ('manual','ocr','voice','whatsapp','email'));

-- ============================================================
-- 2) email_grants — one row per linked Gmail account
-- ============================================================
-- The Gmail watch() push delivers { emailAddress, historyId }. We look the grant
-- up by email, resolve the owning user_id, and insert expenses on their behalf
-- using the service-role key (same pattern as the WhatsApp backend plugin).
create table if not exists public.email_grants (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    email text not null,
    -- Vault secret id holding the encrypted Google OAuth refresh token.
    -- Never store the raw token in this table. NULL until the grant is saved.
    google_refresh_token_secret_id uuid,
    -- Gmail historyId cursor: the last message change we have processed.
    -- Updated after each successful webhook so we only fetch deltas.
    history_id text,
    created_at timestamptz default now(),
    updated_at timestamptz default now(),
    unique (user_id, email),
    unique (email)
);

create index if not exists idx_email_grants_user on public.email_grants(user_id);
create index if not exists idx_email_grants_email on public.email_grants(email);

-- ============================================================
-- RLS — a user only sees/edits their own grants.
-- The service-role key used by the Edge Function bypasses these policies,
-- exactly like the WhatsApp plugin (see SYSTEM_DESIGN.md §6.3).
-- ============================================================
alter table public.email_grants enable row level security;

drop policy if exists email_grants_rw_own on public.email_grants;
create policy email_grants_rw_own
    on public.email_grants
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- updated_at touch (mirrors the users table trigger pattern).
create or replace function public.touch_email_grants_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists email_grants_touch_updated_at on public.email_grants;
create trigger email_grants_touch_updated_at
    before update on public.email_grants
    for each row execute function public.touch_email_grants_updated_at();
