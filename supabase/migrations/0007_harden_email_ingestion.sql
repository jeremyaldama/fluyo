-- Fluyo — harden Gmail receipt ingestion
--
-- This migration intentionally keeps all plaintext OAuth credentials behind
-- SECURITY DEFINER RPCs that only service_role may execute. Authenticated
-- clients can read their own connection metadata, but cannot mutate grants,
-- cursors, Vault references, or sync diagnostics.

create extension if not exists supabase_vault with schema vault;

-- ============================================================
-- 1) Stable source-level idempotency for imported expenses
-- ============================================================
-- Gmail callers use
-- "gmail:v1:<mailbox_fingerprint>:<gmail_message_id>". Other sources keep NULL.
alter table public.expenses
    add column if not exists source_reference text;

-- A deployment that already imported email expenses predates source_reference.
-- Give those rows a stable, unique legacy reference so the invariant can be
-- validated without deleting user data.
update public.expenses
set source_reference = 'legacy:' || id::text
where source = 'email'
  and source_reference is null;

update public.expenses
set source_reference = null
where source <> 'email'
  and source_reference is not null;

do $$
begin
    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.expenses'::regclass
          and conname = 'expenses_source_reference_check'
    ) then
        alter table public.expenses
            add constraint expenses_source_reference_check
            check (
                (
                    source = 'email'
                    and user_id is not null
                    and nullif(btrim(source_reference), '') is not null
                    and length(source_reference) <= 512
                )
                or (source <> 'email' and source_reference is null)
            ) not valid;
    end if;
end;
$$;

alter table public.expenses
    validate constraint expenses_source_reference_check;

create unique index if not exists expenses_user_source_reference_key
    on public.expenses (user_id, source, source_reference);

comment on column public.expenses.source_reference is
    'Idempotency key scoped by user and source; Gmail uses gmail:v1:<mailbox_fingerprint>:<gmail_message_id>.';

-- ============================================================
-- 2) Grant/watch metadata and canonical values
-- ============================================================
alter table public.email_grants
    add column if not exists watch_expiration timestamptz,
    add column if not exists last_synced_at timestamptz,
    add column if not exists last_error text,
    add column if not exists mailbox_fingerprint text;

create index if not exists idx_email_grants_watch_expiration
    on public.email_grants (watch_expiration);

-- Trigger-owned Vault cleanup.  A Vault UUID in the original 0006 table was
-- user-writable, so only delete secrets whose name proves they belong to the
-- grant. This also runs for ON DELETE CASCADE when a Fluyo user is deleted.
create or replace function public.delete_email_grant_vault_secret()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_secret_name text;
begin
    if old.google_refresh_token_secret_id is null then
        return old;
    end if;

    select s.name
    into v_secret_name
    from vault.secrets as s
    where s.id = old.google_refresh_token_secret_id;

    if v_secret_name in (
        'gmail-refresh:' || old.email,
        'fluyo:gmail-refresh:' || old.id::text
    ) and not exists (
        select 1
        from public.email_grants as other_grant
        where other_grant.id <> old.id
          and other_grant.google_refresh_token_secret_id = old.google_refresh_token_secret_id
    ) then
        delete from vault.secrets as managed_secret
        where managed_secret.id = old.google_refresh_token_secret_id;
    end if;

    return old;
end;
$$;

revoke execute on function public.delete_email_grant_vault_secret() from public;
revoke execute on function public.delete_email_grant_vault_secret() from anon;
revoke execute on function public.delete_email_grant_vault_secret() from authenticated;

drop trigger if exists email_grants_delete_vault_secret on public.email_grants;
create trigger email_grants_delete_vault_secret
    before delete on public.email_grants
    for each row execute function public.delete_email_grant_vault_secret();

-- Product semantics are one Gmail account per Fluyo user. Keep the most
-- recently updated grant if pre-0007 data contains more than one. The trigger
-- above safely removes a managed Vault secret for discarded rows.
do $$
declare
    duplicate_grant record;
begin
    for duplicate_grant in
        select ranked.id
        from (
            select
                eg.id,
                row_number() over (
                    partition by eg.user_id
                    order by eg.updated_at desc nulls last,
                             eg.created_at desc nulls last,
                             eg.id desc
                ) as position
            from public.email_grants as eg
        ) as ranked
        where ranked.position > 1
    loop
        delete from public.email_grants
        where id = duplicate_grant.id;
    end loop;

    -- Email uniqueness in 0006 was case-sensitive. Resolve any legacy
    -- case-only collision before canonicalizing addresses.
    for duplicate_grant in
        select ranked.id
        from (
            select
                eg.id,
                row_number() over (
                    partition by lower(btrim(eg.email))
                    order by eg.updated_at desc nulls last,
                             eg.created_at desc nulls last,
                             eg.id desc
                ) as position
            from public.email_grants as eg
        ) as ranked
        where ranked.position > 1
    loop
        delete from public.email_grants
        where id = duplicate_grant.id;
    end loop;
end;
$$;

-- Rename verified 0006-era secrets before canonicalizing email case. This
-- preserves the credential while making every managed secret unambiguously
-- bound to its immutable grant UUID.
do $$
declare
    managed_grant record;
begin
    for managed_grant in
        select
            eg.id,
            eg.google_refresh_token_secret_id as secret_id
        from public.email_grants as eg
        join vault.secrets as secret
          on secret.id = eg.google_refresh_token_secret_id
        where secret.name = 'gmail-refresh:' || eg.email
    loop
        perform vault.update_secret(
            managed_grant.secret_id,
            null,
            'fluyo:gmail-refresh:' || managed_grant.id::text,
            'Fluyo Gmail OAuth refresh token'
        );
    end loop;
end;
$$;

update public.email_grants
set email = lower(btrim(email))
where email is distinct from lower(btrim(email));

do $$
begin
    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.email_grants'::regclass
          and conname = 'email_grants_user_id_key'
    ) then
        alter table public.email_grants
            add constraint email_grants_user_id_key unique (user_id);
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.email_grants'::regclass
          and conname = 'email_grants_email_canonical_check'
    ) then
        alter table public.email_grants
            add constraint email_grants_email_canonical_check
            check (
                email = lower(btrim(email))
                and length(email) between 3 and 320
            ) not valid;
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.email_grants'::regclass
          and conname = 'email_grants_history_id_check'
    ) then
        alter table public.email_grants
            add constraint email_grants_history_id_check
            check (
                history_id is null
                or (length(history_id) <= 64 and history_id ~ '^[0-9]+$')
            ) not valid;
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.email_grants'::regclass
          and conname = 'email_grants_mailbox_fingerprint_check'
    ) then
        alter table public.email_grants
            add constraint email_grants_mailbox_fingerprint_check
            check (
                mailbox_fingerprint is null
                or (
                    length(mailbox_fingerprint) = 43
                    and mailbox_fingerprint ~ '^[A-Za-z0-9_-]{43}$'
                )
            ) not valid;
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_constraint
        where conrelid = 'public.email_grants'::regclass
          and conname = 'email_grants_mailbox_fingerprint_key'
    ) then
        alter table public.email_grants
            add constraint email_grants_mailbox_fingerprint_key
            unique (mailbox_fingerprint);
    end if;
end;
$$;

-- An early 0007 draft used a NOT VALID non-NULL check. PostgreSQL also applies
-- such a check to later diagnostic/watch UPDATEs, which made legacy grants
-- impossible to mark for attention. Writes are already RPC/service-role only,
-- and upsert_email_grant requires a fingerprint, so retain NULL solely as the
-- fail-closed legacy state and remove that over-broad draft constraint.
alter table public.email_grants
    drop constraint if exists email_grants_new_rows_require_fingerprint;

alter table public.email_grants
    validate constraint email_grants_email_canonical_check;
alter table public.email_grants
    validate constraint email_grants_history_id_check;
alter table public.email_grants
    validate constraint email_grants_mailbox_fingerprint_check;

-- Harden the pre-existing trigger function as well.
create or replace function public.touch_email_grants_updated_at()
returns trigger
language plpgsql
set search_path = pg_catalog
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

revoke execute on function public.touch_email_grants_updated_at() from public;
revoke execute on function public.touch_email_grants_updated_at() from anon;
revoke execute on function public.touch_email_grants_updated_at() from authenticated;

-- ============================================================
-- 3) SELECT-only RLS for the Android client
-- ============================================================
drop policy if exists email_grants_rw_own on public.email_grants;
drop policy if exists email_grants_select_own on public.email_grants;

create policy email_grants_select_own
    on public.email_grants
    for select
    to authenticated
    using (
        user_id in (
            select u.id
            from public.users as u
            where u.auth_id = auth.uid()
        )
    );

revoke all on table public.email_grants from anon;
revoke all on table public.email_grants from authenticated;
grant select (
    email,
    watch_expiration,
    last_synced_at,
    last_error,
    created_at,
    updated_at
) on table public.email_grants to authenticated;
grant all on table public.email_grants to service_role;

-- ============================================================
-- 4) Vault-backed, service-role-only RPC contract
-- ============================================================

-- Insert/relink one grant and atomically create or rotate its Vault secret.
-- mailbox_fingerprint is the stable HMAC-SHA256/base64url identity used by the
-- expense idempotency key. A different fingerprint receives a new grant id.
-- Remove the pre-fingerprint overload if an earlier draft of 0007 was applied.
drop function if exists public.upsert_email_grant(uuid, text, text, text, timestamptz);

create or replace function public.upsert_email_grant(
    p_user_id uuid,
    p_email text,
    p_mailbox_fingerprint text,
    p_refresh_token text,
    p_history_id text default null,
    p_watch_expiration timestamptz default null
)
returns table (
    grant_id uuid,
    email text,
    history_id text,
    watch_expiration timestamptz
)
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_email text;
    v_grant public.email_grants%rowtype;
    v_grant_id uuid;
    v_secret_id uuid;
    v_secret_name text;
begin
    v_email := lower(btrim(p_email));

    if p_user_id is null or not exists (
        select 1 from public.users as u where u.id = p_user_id
    ) then
        raise exception using errcode = '22023', message = 'invalid_user';
    end if;
    if v_email is null or length(v_email) not between 3 and 320 then
        raise exception using errcode = '22023', message = 'invalid_email';
    end if;
    if p_mailbox_fingerprint is null
       or length(p_mailbox_fingerprint) <> 43
       or p_mailbox_fingerprint !~ '^[A-Za-z0-9_-]{43}$' then
        raise exception using errcode = '22023', message = 'invalid_mailbox_fingerprint';
    end if;
    if p_refresh_token is null
       or length(p_refresh_token) not between 1 and 8192 then
        raise exception using errcode = '22023', message = 'invalid_refresh_token';
    end if;
    if p_history_id is not null and (
        length(p_history_id) > 64 or p_history_id !~ '^[0-9]+$'
    ) then
        raise exception using errcode = '22023', message = 'invalid_history_id';
    end if;
    if exists (
        select 1
        from public.email_grants as by_email
        where by_email.email = v_email
          and by_email.user_id <> p_user_id
    ) then
        raise exception using errcode = '23505', message = 'gmail_account_already_linked';
    end if;
    if exists (
        select 1
        from public.email_grants as by_fingerprint
        where by_fingerprint.mailbox_fingerprint = p_mailbox_fingerprint
          and by_fingerprint.user_id <> p_user_id
    ) then
        raise exception using errcode = '23505', message = 'gmail_account_already_linked';
    end if;

    select eg.*
    into v_grant
    from public.email_grants as eg
    where eg.user_id = p_user_id
    for update;

    if found and (
        v_grant.mailbox_fingerprint = p_mailbox_fingerprint
        or (
            v_grant.mailbox_fingerprint is null
            and v_grant.email = v_email
        )
    ) then
        v_grant_id := v_grant.id;
        v_secret_id := v_grant.google_refresh_token_secret_id;

        select s.name
        into v_secret_name
        from vault.secrets as s
        where s.id = v_secret_id;

        -- Never overwrite an arbitrary Vault UUID that may have been injected
        -- while the permissive 0006 policy was active.
        if v_secret_name in (
            'gmail-refresh:' || v_grant.email,
            'fluyo:gmail-refresh:' || v_grant.id::text
        ) then
            perform vault.update_secret(
                v_secret_id,
                p_refresh_token,
                'fluyo:gmail-refresh:' || v_grant.id::text,
                'Fluyo Gmail OAuth refresh token'
            );
        else
            v_secret_id := vault.create_secret(
                p_refresh_token,
                'fluyo:gmail-refresh:' || v_grant.id::text,
                'Fluyo Gmail OAuth refresh token'
            );
        end if;

        update public.email_grants as eg
        set email = v_email,
            mailbox_fingerprint = p_mailbox_fingerprint,
            google_refresh_token_secret_id = v_secret_id,
            -- Reauthorization/watch renewal must not jump past changes that
            -- have not yet been consumed from the existing Gmail cursor.
            history_id = coalesce(eg.history_id, p_history_id),
            watch_expiration = p_watch_expiration,
            last_error = null
        where eg.id = v_grant_id;
    else
        -- When relinking to another mailbox, delete the old grant first. The
        -- trigger removes its managed secret in the same transaction.
        if v_grant.id is not null then
            delete from public.email_grants as old_grant
            where old_grant.id = v_grant.id;
        end if;

        v_grant_id := gen_random_uuid();
        v_secret_id := vault.create_secret(
            p_refresh_token,
            'fluyo:gmail-refresh:' || v_grant_id::text,
            'Fluyo Gmail OAuth refresh token'
        );

        insert into public.email_grants (
            id,
            user_id,
            email,
            mailbox_fingerprint,
            google_refresh_token_secret_id,
            history_id,
            watch_expiration,
            last_error
        ) values (
            v_grant_id,
            p_user_id,
            v_email,
            p_mailbox_fingerprint,
            v_secret_id,
            p_history_id,
            p_watch_expiration,
            null
        );
    end if;

    return query
    select eg.id, eg.email, eg.history_id, eg.watch_expiration
    from public.email_grants as eg
    where eg.id = v_grant_id;
end;
$$;

-- Resolve a push notification's mailbox and credential in one server-side
-- operation. Plaintext exists only in this service-role response. LEFT JOIN is
-- intentional: a broken/missing Vault reference remains visible so Edge can
-- fail closed, expose diagnostics, and disconnect the grant.
drop function if exists public.get_email_grant_for_sync(text);

create or replace function public.get_email_grant_for_sync(p_email text)
returns table (
    grant_id uuid,
    user_id uuid,
    email text,
    mailbox_fingerprint text,
    refresh_token text,
    history_id text,
    watch_expiration timestamptz,
    last_synced_at timestamptz,
    created_at timestamptz
)
language sql
security definer
set search_path = pg_catalog
stable
as $$
    select
        eg.id,
        eg.user_id,
        eg.email,
        eg.mailbox_fingerprint,
        decrypted.decrypted_secret,
        eg.history_id,
        eg.watch_expiration,
        eg.last_synced_at,
        eg.created_at
    from public.email_grants as eg
    left join vault.decrypted_secrets as decrypted
      on decrypted.id = eg.google_refresh_token_secret_id
     and decrypted.name = 'fluyo:gmail-refresh:' || eg.id::text
    where eg.email = lower(btrim(p_email))
$$;

-- Older drafts exposed a user's decrypted token list for best-effort Google
-- cleanup. That snapshot-based cleanup races a newer relink, so remove the RPC
-- entirely; current DELETE never decrypts a token.
drop function if exists public.get_email_grants_for_user(uuid);

-- Renewal workers call this with (for example) now() + interval '24 hours'.
-- Include the nullable fingerprint so cron can mark a legacy grant for relink
-- instead of renewing a watch that is intentionally inactive for inserts.
drop function if exists public.list_email_grants_due_for_renewal(timestamptz);

create or replace function public.list_email_grants_due_for_renewal(
    p_before timestamptz
)
returns table (
    grant_id uuid,
    user_id uuid,
    email text,
    mailbox_fingerprint text,
    refresh_token text,
    history_id text,
    watch_expiration timestamptz
)
language sql
security definer
set search_path = pg_catalog
stable
as $$
    select
        eg.id,
        eg.user_id,
        eg.email,
        eg.mailbox_fingerprint,
        decrypted.decrypted_secret,
        eg.history_id,
        eg.watch_expiration
    from public.email_grants as eg
    left join vault.decrypted_secrets as decrypted
      on decrypted.id = eg.google_refresh_token_secret_id
     and decrypted.name = 'fluyo:gmail-refresh:' || eg.id::text
    where eg.watch_expiration is null
       or eg.watch_expiration <= p_before
    order by eg.watch_expiration asc nulls first, eg.id
$$;

-- Close the authorization race between reading/parsing Gmail and writing the
-- expense. The grant row remains locked through category resolution, dedupe,
-- and INSERT, so a concurrent disconnect either wins first (inactive) or waits
-- until this already-authorized insert commits. Callers cannot supply user_id,
-- category_id, or source_reference.
create or replace function public.insert_email_expense_if_grant_active(
    p_grant_id uuid,
    p_message_id text,
    p_amount numeric,
    p_description text,
    p_expense_date date,
    p_recipient text
)
returns text
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_user_id uuid;
    v_mailbox_fingerprint text;
    v_source_reference text;
    v_category_id uuid;
begin
    if p_grant_id is null then
        raise exception using errcode = '22023', message = 'invalid_grant_id';
    end if;
    if p_message_id is null
       or length(p_message_id) not between 1 and 256
       or p_message_id !~ '^[A-Za-z0-9_-]+$' then
        raise exception using errcode = '22023', message = 'invalid_message_id';
    end if;
    if p_amount is null
       or p_amount <= 0
       or p_amount > 99999999.99
       or p_amount <> round(p_amount, 2) then
        raise exception using errcode = '22023', message = 'invalid_amount';
    end if;
    if p_description is not null and (
        length(p_description) > 240 or p_description ~ E'[\\r\\n]'
    ) then
        raise exception using errcode = '22023', message = 'invalid_description';
    end if;
    if p_recipient is not null and (
        length(p_recipient) > 120 or p_recipient ~ E'[\\r\\n]'
    ) then
        raise exception using errcode = '22023', message = 'invalid_recipient';
    end if;
    if p_expense_date is null
       or p_expense_date < date '2000-01-01'
       or p_expense_date > current_date + 1 then
        raise exception using errcode = '22023', message = 'invalid_expense_date';
    end if;

    select eg.user_id, eg.mailbox_fingerprint
    into v_user_id, v_mailbox_fingerprint
    from public.email_grants as eg
    where eg.id = p_grant_id
    for update;

    if not found or v_mailbox_fingerprint is null then
        return 'inactive';
    end if;

    v_source_reference :=
        'gmail:v1:' || v_mailbox_fingerprint || ':' || p_message_id;

    select c.id
    into v_category_id
    from public.categories as c
    where c.user_id = v_user_id
      and lower(btrim(c.name)) = 'otros'
    order by c.is_default desc nulls last,
             c.display_order asc nulls last,
             c.created_at asc nulls last,
             c.id
    limit 1;

    insert into public.expenses (
        user_id,
        amount,
        category_id,
        description,
        expense_date,
        source,
        recipient,
        source_reference
    ) values (
        v_user_id,
        p_amount,
        v_category_id,
        nullif(btrim(p_description), ''),
        p_expense_date,
        'email',
        nullif(btrim(p_recipient), ''),
        v_source_reference
    )
    on conflict (user_id, source, source_reference) do nothing;

    if found then
        return 'inserted';
    end if;
    return 'duplicate';
end;
$$;

-- Gmail history ids are decimal counters that can exceed bigint. Numeric gives
-- us an exact monotonic comparison and the row lock prevents cursor regression
-- when two Pub/Sub notifications finish out of order.
create or replace function public.advance_email_grant_cursor(
    grant_id uuid,
    new_history_id text,
    synced_at timestamptz default now()
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_current_history_id text;
    v_should_advance boolean;
begin
    if new_history_id is null
       or length(new_history_id) > 64
       or new_history_id !~ '^[0-9]+$' then
        raise exception using errcode = '22023', message = 'invalid_history_id';
    end if;

    select eg.history_id
    into v_current_history_id
    from public.email_grants as eg
    where eg.id = grant_id
    for update;

    if not found then
        return false;
    end if;

    v_should_advance := v_current_history_id is null
        or new_history_id::numeric > v_current_history_id::numeric;

    update public.email_grants as eg
    set history_id = case
            when v_should_advance then new_history_id
            else eg.history_id
        end,
        last_synced_at = coalesce(synced_at, now()),
        last_error = null
    where eg.id = grant_id;

    return v_should_advance;
end;
$$;

-- Persist a renewed watch without skipping messages: the watch response's
-- history id only seeds an empty cursor and never replaces an existing cursor.
create or replace function public.update_email_grant_watch(
    grant_id uuid,
    watch_expiration timestamptz,
    initial_history_id text default null
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
begin
    if watch_expiration is null then
        raise exception using errcode = '22023', message = 'invalid_watch_expiration';
    end if;
    if initial_history_id is not null and (
        length(initial_history_id) > 64 or initial_history_id !~ '^[0-9]+$'
    ) then
        raise exception using errcode = '22023', message = 'invalid_history_id';
    end if;

    update public.email_grants as eg
    set watch_expiration = update_email_grant_watch.watch_expiration,
        history_id = coalesce(eg.history_id, initial_history_id),
        last_error = case
            -- Reaching this RPC means both refresh-token exchange and watch()
            -- succeeded, so either diagnostic is now demonstrably stale.
            when eg.last_error in ('watch_failed', 'token_refresh_failed') then null
            else eg.last_error
        end
    where eg.id = grant_id;

    return found;
end;
$$;

-- Store a bounded diagnostic code, never a raw provider response or token.
create or replace function public.mark_email_grant_sync_error(
    grant_id uuid,
    error_message text
)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
declare
    v_error_code text;
begin
    v_error_code := left(
        regexp_replace(
            coalesce(nullif(btrim(error_message), ''), 'sync_failed'),
            '[^a-zA-Z0-9_.:-]+',
            '_',
            'g'
        ),
        128
    );

    update public.email_grants as eg
    set last_error = v_error_code
    where eg.id = grant_id;

    return found;
end;
$$;

-- Deletion invokes email_grants_delete_vault_secret, including cascades. Remove
-- the old grant-id RPC so callers cannot act on a stale snapshot; unlink is a
-- single user-scoped operation.
drop function if exists public.disconnect_email_grant(uuid);

create or replace function public.disconnect_email_grant_for_user(p_user_id uuid)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
begin
    delete from public.email_grants as eg
    where eg.user_id = p_user_id;
    return found;
end;
$$;

-- PostgreSQL grants EXECUTE to PUBLIC for new functions by default. Remove it
-- explicitly and expose these credential-bearing operations only to the Edge
-- Functions' service-role JWT.
revoke execute on function public.upsert_email_grant(uuid, text, text, text, text, timestamptz)
    from public, anon, authenticated;
revoke execute on function public.get_email_grant_for_sync(text)
    from public, anon, authenticated;
revoke execute on function public.list_email_grants_due_for_renewal(timestamptz)
    from public, anon, authenticated;
revoke execute on function public.insert_email_expense_if_grant_active(uuid, text, numeric, text, date, text)
    from public, anon, authenticated;
revoke execute on function public.advance_email_grant_cursor(uuid, text, timestamptz)
    from public, anon, authenticated;
revoke execute on function public.update_email_grant_watch(uuid, timestamptz, text)
    from public, anon, authenticated;
revoke execute on function public.mark_email_grant_sync_error(uuid, text)
    from public, anon, authenticated;
revoke execute on function public.disconnect_email_grant_for_user(uuid)
    from public, anon, authenticated;

grant execute on function public.upsert_email_grant(uuid, text, text, text, text, timestamptz)
    to service_role;
grant execute on function public.get_email_grant_for_sync(text)
    to service_role;
grant execute on function public.list_email_grants_due_for_renewal(timestamptz)
    to service_role;
grant execute on function public.insert_email_expense_if_grant_active(uuid, text, numeric, text, date, text)
    to service_role;
grant execute on function public.advance_email_grant_cursor(uuid, text, timestamptz)
    to service_role;
grant execute on function public.update_email_grant_watch(uuid, timestamptz, text)
    to service_role;
grant execute on function public.mark_email_grant_sync_error(uuid, text)
    to service_role;
grant execute on function public.disconnect_email_grant_for_user(uuid)
    to service_role;

comment on column public.email_grants.watch_expiration is
    'Expiration returned by Gmail users.watch; renew before this instant.';
comment on column public.email_grants.last_synced_at is
    'Last successful sync attempt, including an idempotent replay.';
comment on column public.email_grants.last_error is
    'Sanitized provider-independent error code, maximum 128 characters.';
comment on column public.email_grants.mailbox_fingerprint is
    'Stable 43-character base64url HMAC fingerprint; NULL only on legacy grants awaiting relink.';
