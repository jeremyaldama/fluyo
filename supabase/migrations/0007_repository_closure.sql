-- 0007: repository closure — race-safe creation paths, deletion tombstones,
-- server-derived streaks and remaining integrity contracts.
--
-- This is an EXPAND migration. Deploy it before the Android client that calls the
-- new RPCs. The separate 0008 contract migration revokes legacy direct INSERT/UPDATE
-- paths only after old clients have been retired.

-- ============================================================
-- SCHEMA EXTENSIONS FOR IDEMPOTENCY AND SOFT DELETION
-- ============================================================

alter table public.users
    add column if not exists deletion_started_at timestamptz;

alter table public.expenses
    add column if not exists client_request_id uuid;

alter table public.goals
    add column if not exists client_request_id uuid,
    add column if not exists deleted_at timestamptz;

alter table public.budget_extras
    add column if not exists client_request_id uuid;

create unique index if not exists expenses_user_client_request_unique
    on public.expenses (user_id, client_request_id)
    where client_request_id is not null;

create index if not exists expenses_user_created_cursor
    on public.expenses (user_id, created_at desc, id desc);

create unique index if not exists goals_user_client_request_unique
    on public.goals (user_id, client_request_id)
    where client_request_id is not null;

create unique index if not exists budget_extras_user_client_request_unique
    on public.budget_extras (user_id, client_request_id)
    where client_request_id is not null;

create index if not exists goals_user_visible_created
    on public.goals (user_id, created_at desc, id desc)
    where deleted_at is null;

-- ============================================================
-- ACCOUNT-DELETION TOMBSTONE
-- ============================================================

-- Every user-owned write, including service-role writes from companion services,
-- must stop once deletion begins. DELETE remains possible so cleanup can proceed.
create or replace function public.reject_write_for_deleting_owner()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.user_id is null then
        return new;
    end if;

    if not exists (
        select 1
        from public.users as u
        where u.id = new.user_id
          and u.deletion_started_at is null
    ) then
        raise exception using
            errcode = '55000',
            message = 'Account deletion is in progress';
    end if;
    return new;
end;
$$;

do $install_owner_guards$
declare
    guarded_table text;
begin
    foreach guarded_table in array array[
        'categories', 'expenses', 'goals', 'badges', 'goal_deposits',
        'budget_extras', 'whatsapp_links', 'whatsapp_link_challenges'
    ]
    loop
        execute format(
            'drop trigger if exists reject_write_for_deleting_owner on public.%I',
            guarded_table
        );
        execute format(
            'create trigger reject_write_for_deleting_owner before insert or update on public.%I '
            'for each row execute function public.reject_write_for_deleting_owner()',
            guarded_table
        );
    end loop;
end;
$install_owner_guards$;

revoke execute on function public.reject_write_for_deleting_owner()
    from public, anon, authenticated, service_role;

drop policy if exists users_update_own on public.users;
create policy users_update_own
    on public.users
    for update
    using (auth.uid() = auth_id and deletion_started_at is null)
    with check (auth.uid() = auth_id and deletion_started_at is null);

create or replace function public.begin_account_deletion(p_auth_id uuid)
returns table (tombstoned boolean)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_auth_id is null then
        raise exception using errcode = '22023', message = 'Auth identity is required';
    end if;

    update public.users as u
    set deletion_started_at = coalesce(u.deletion_started_at, pg_catalog.now())
    where u.auth_id = p_auth_id;

    return query select found;
end;
$$;

revoke execute on function public.begin_account_deletion(uuid)
    from public, anon, authenticated;
grant execute on function public.begin_account_deletion(uuid) to service_role;

-- ============================================================
-- VERSIONED PRIVATE STORAGE CONTRACT
-- ============================================================

-- Hosted Supabase provides storage.buckets/storage.objects. The conditional block
-- keeps vanilla PostgreSQL migration tests usable while their bootstrap can also
-- provide a compatible mock and exercise these policies.
do $storage_contract$
begin
    if to_regclass('storage.buckets') is not null
       and to_regclass('storage.objects') is not null
    then
        execute $sql$
            insert into storage.buckets (
                id, name, public, file_size_limit, allowed_mime_types
            ) values (
                'receipts', 'receipts', false, 10485760,
                array['image/jpeg', 'image/png', 'image/webp']::text[]
            )
            on conflict (id) do update
            set public = false,
                file_size_limit = excluded.file_size_limit,
                allowed_mime_types = excluded.allowed_mime_types
        $sql$;

        execute 'alter table storage.objects enable row level security';

        execute 'drop policy if exists fluyo_receipts_select_own on storage.objects';
        execute $policy$
            create policy fluyo_receipts_select_own
            on storage.objects for select to authenticated
            using (
                bucket_id = 'receipts'
                and (storage.foldername(name))[1] = auth.uid()::text
            )
        $policy$;

        execute 'drop policy if exists fluyo_receipts_insert_active_own on storage.objects';
        execute $policy$
            create policy fluyo_receipts_insert_active_own
            on storage.objects for insert to authenticated
            with check (
                bucket_id = 'receipts'
                and (storage.foldername(name))[1] = auth.uid()::text
                and exists (
                    select 1 from public.users as u
                    where u.auth_id = auth.uid()
                      and u.deletion_started_at is null
                )
            )
        $policy$;

        execute 'drop policy if exists fluyo_receipts_update_active_own on storage.objects';
        execute $policy$
            create policy fluyo_receipts_update_active_own
            on storage.objects for update to authenticated
            using (
                bucket_id = 'receipts'
                and (storage.foldername(name))[1] = auth.uid()::text
            )
            with check (
                bucket_id = 'receipts'
                and (storage.foldername(name))[1] = auth.uid()::text
                and exists (
                    select 1 from public.users as u
                    where u.auth_id = auth.uid()
                      and u.deletion_started_at is null
                )
            )
        $policy$;

        execute 'drop policy if exists fluyo_receipts_delete_own on storage.objects';
        execute $policy$
            create policy fluyo_receipts_delete_own
            on storage.objects for delete to authenticated
            using (
                bucket_id = 'receipts'
                and (storage.foldername(name))[1] = auth.uid()::text
            )
        $policy$;

        execute 'grant select, insert, update, delete on storage.objects to authenticated';
        execute 'grant all on storage.objects to service_role';
    end if;
end;
$storage_contract$;

-- ============================================================
-- RACE-SAFE PROFILE PROVISIONING
-- ============================================================

create or replace function public.ensure_user_profile()
returns setof public.users
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_auth_id uuid := auth.uid();
    auth_email text;
    auth_name text;
    profile public.users%rowtype;
begin
    if caller_auth_id is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;

    select a.email,
           left(
               nullif(btrim(coalesce(
                   a.raw_user_meta_data ->> 'full_name',
                   a.raw_user_meta_data ->> 'name'
               )), ''),
               100
           )
    into auth_email, auth_name
    from auth.users as a
    where a.id = caller_auth_id;

    if not found then
        raise exception using errcode = '28000', message = 'Authenticated identity not found';
    end if;

    insert into public.users (auth_id, email, display_name)
    values (caller_auth_id, auth_email, auth_name)
    on conflict (auth_id) do nothing;

    select u.* into profile
    from public.users as u
    where u.auth_id = caller_auth_id;

    if profile.id is null then
        raise exception using errcode = '55000', message = 'Profile provisioning failed';
    end if;
    if profile.deletion_started_at is not null then
        raise exception using errcode = '55000', message = 'Account deletion is in progress';
    end if;

    return next profile;
end;
$$;

revoke execute on function public.ensure_user_profile() from public, anon;
grant execute on function public.ensure_user_profile() to authenticated;

-- ============================================================
-- EXACT, IDEMPOTENT CREATE OPERATIONS
-- ============================================================

create or replace function public.validate_expense_business_date()
returns trigger
language plpgsql
set search_path = ''
as $$
declare
    lima_today date := pg_catalog.timezone('America/Lima', pg_catalog.now())::date;
begin
    if new.expense_date is null
       or new.expense_date < date '2000-01-01'
       or new.expense_date > lima_today
    then
        raise exception using
            errcode = '22007',
            message = 'Expense date must be between 2000-01-01 and today';
    end if;
    return new;
end;
$$;

drop trigger if exists expenses_validate_business_date on public.expenses;
create trigger expenses_validate_business_date
    before insert or update of expense_date on public.expenses
    for each row execute function public.validate_expense_business_date();

revoke execute on function public.validate_expense_business_date()
    from public, anon, authenticated, service_role;

create or replace function public.create_expense(
    p_request_id uuid,
    p_amount numeric,
    p_category_id uuid,
    p_description text,
    p_expense_date date,
    p_source text,
    p_recipient text,
    p_image_url text
)
returns setof public.expenses
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    normalized_description text := nullif(btrim(p_description), '');
    normalized_recipient text := nullif(btrim(p_recipient), '');
    existing public.expenses%rowtype;
    inserted public.expenses%rowtype;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;
    if p_request_id is null then
        raise exception using errcode = '22023', message = 'A request id is required';
    end if;
    if p_amount is null
       or p_amount::text in ('NaN', 'Infinity', '-Infinity')
       or p_amount <= 0
       or p_amount > 99999999.99
       or p_amount <> round(p_amount, 2)
    then
        raise exception using errcode = '22023', message = 'Invalid expense amount';
    end if;
    if p_source not in ('manual', 'ocr', 'voice', 'whatsapp') then
        raise exception using errcode = '22023', message = 'Invalid expense source';
    end if;

    -- Serialize the ledger high-water mark used by expense_page. The insert assigns
    -- created_at only after this lock, so pages can exclude every later write exactly.
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(caller_user_id::text || ':expense-ledger', 0)
    );
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(caller_user_id::text || ':expense:' || p_request_id::text, 0)
    );

    select e.* into existing
    from public.expenses as e
    where e.user_id = caller_user_id and e.client_request_id = p_request_id;

    if found then
        if existing.amount is distinct from p_amount::numeric(10,2)
           or existing.category_id is distinct from p_category_id
           or existing.description is distinct from normalized_description
           or existing.expense_date is distinct from p_expense_date
           or existing.source is distinct from p_source
           or existing.recipient is distinct from normalized_recipient
           or existing.image_url is distinct from p_image_url
        then
            raise exception using errcode = '22023', message = 'Request id reused with different expense data';
        end if;
        return next existing;
        return;
    end if;

    insert into public.expenses (
        user_id, amount, category_id, description, expense_date, source,
        recipient, image_url, client_request_id, created_at
    ) values (
        caller_user_id, p_amount, p_category_id, normalized_description,
        p_expense_date, p_source, normalized_recipient, p_image_url, p_request_id,
        pg_catalog.clock_timestamp()
    ) returning * into inserted;

    return next inserted;
end;
$$;

revoke execute on function public.create_expense(uuid, numeric, uuid, text, date, text, text, text)
    from public, anon;
grant execute on function public.create_expense(uuid, numeric, uuid, text, date, text, text, text)
    to authenticated;

-- Stable keyset pagination for exports and statistics. The first call omits
-- p_snapshot_at and obtains a high-water timestamp while holding the same owner lock
-- as create_expense. Later calls reuse both snapshot_at and the final row cursor, so
-- new expenses cannot shift, duplicate or disappear between pages.
create or replace function public.expense_page(
    p_from date,
    p_to date,
    p_snapshot_at timestamptz default null,
    p_before_created_at timestamptz default null,
    p_before_id uuid default null,
    p_page_size integer default 500
)
returns table (
    id uuid,
    user_id uuid,
    amount numeric,
    category_id uuid,
    description text,
    expense_date date,
    source text,
    recipient text,
    image_url text,
    created_at timestamptz,
    snapshot_at timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    effective_snapshot timestamptz;
    lima_today date := pg_catalog.timezone('America/Lima', pg_catalog.now())::date;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;
    if p_from is null or p_to is null
       or p_from < date '2000-01-01'
       or p_to > lima_today
       or p_to < p_from
    then
        raise exception using errcode = '22007', message = 'Invalid expense page date range';
    end if;
    if p_page_size is null or p_page_size not between 1 and 500 then
        raise exception using errcode = '22023', message = 'Expense page size must be between 1 and 500';
    end if;
    if (p_before_created_at is null) <> (p_before_id is null) then
        raise exception using errcode = '22023', message = 'Expense cursor is incomplete';
    end if;

    if p_snapshot_at is null then
        if p_before_created_at is not null then
            raise exception using errcode = '22023', message = 'Expense cursor requires a snapshot';
        end if;
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended(caller_user_id::text || ':expense-ledger', 0)
        );
        effective_snapshot := pg_catalog.clock_timestamp();
    else
        if p_snapshot_at > pg_catalog.clock_timestamp() + interval '5 minutes' then
            raise exception using errcode = '22023', message = 'Expense snapshot is invalid';
        end if;
        effective_snapshot := p_snapshot_at;
    end if;
    if p_before_created_at is not null and p_before_created_at > effective_snapshot then
        raise exception using errcode = '22023', message = 'Expense cursor is outside its snapshot';
    end if;

    return query
    select e.id, e.user_id, e.amount, e.category_id, e.description,
           e.expense_date, e.source, e.recipient, e.image_url, e.created_at,
           effective_snapshot
    from public.expenses as e
    where e.user_id = caller_user_id
      and e.expense_date between p_from and p_to
      and e.created_at <= effective_snapshot
      and (
          p_before_created_at is null
          or (e.created_at, e.id) < (p_before_created_at, p_before_id)
      )
    order by e.created_at desc, e.id desc
    limit p_page_size;
end;
$$;

revoke execute on function public.expense_page(date, date, timestamptz, timestamptz, uuid, integer)
    from public, anon;
grant execute on function public.expense_page(date, date, timestamptz, timestamptz, uuid, integer)
    to authenticated;

create or replace function public.create_goal(
    p_request_id uuid,
    p_name text,
    p_target_amount numeric,
    p_deadline date
)
returns setof public.goals
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    normalized_name text := btrim(p_name);
    lima_today date := pg_catalog.timezone('America/Lima', pg_catalog.now())::date;
    existing public.goals%rowtype;
    inserted public.goals%rowtype;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;
    if p_request_id is null then
        raise exception using errcode = '22023', message = 'A request id is required';
    end if;
    if normalized_name is null or char_length(normalized_name) not between 1 and 120 then
        raise exception using errcode = '22023', message = 'Invalid goal name';
    end if;
    if p_target_amount is null
       or p_target_amount::text in ('NaN', 'Infinity', '-Infinity')
       or p_target_amount <= 0
       or p_target_amount > 99999999.99
       or p_target_amount <> round(p_target_amount, 2)
    then
        raise exception using errcode = '22023', message = 'Invalid goal target';
    end if;
    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(caller_user_id::text || ':goal:' || p_request_id::text, 0)
    );

    select g.* into existing
    from public.goals as g
    where g.user_id = caller_user_id and g.client_request_id = p_request_id;

    if found then
        if existing.name is distinct from normalized_name
           or existing.target_amount is distinct from p_target_amount::numeric(10,2)
           or existing.deadline is distinct from p_deadline
        then
            raise exception using errcode = '22023', message = 'Request id reused with different goal data';
        end if;
        return next existing;
        return;
    end if;

    -- Validate only new writes. An exact idempotent replay must keep succeeding even
    -- after its once-future deadline naturally becomes a past date.
    if p_deadline is not null and p_deadline < lima_today then
        raise exception using errcode = '22023', message = 'Goal deadline cannot be in the past';
    end if;

    insert into public.goals (
        user_id, name, target_amount, deadline, client_request_id
    ) values (
        caller_user_id, normalized_name, p_target_amount, p_deadline, p_request_id
    ) returning * into inserted;

    return next inserted;
end;
$$;

revoke execute on function public.create_goal(uuid, text, numeric, date) from public, anon;
grant execute on function public.create_goal(uuid, text, numeric, date) to authenticated;

create or replace function public.create_budget_extra(
    p_request_id uuid,
    p_amount numeric,
    p_note text,
    p_month date
)
returns setof public.budget_extras
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    normalized_note text := nullif(btrim(p_note), '');
    current_month date := date_trunc('month', pg_catalog.timezone('America/Lima', pg_catalog.now()))::date;
    existing public.budget_extras%rowtype;
    inserted public.budget_extras%rowtype;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;
    if p_request_id is null then
        raise exception using errcode = '22023', message = 'A request id is required';
    end if;
    if p_amount is null
       or p_amount::text in ('NaN', 'Infinity', '-Infinity')
       or p_amount <= 0
       or p_amount > 99999999.99
       or p_amount <> round(p_amount, 2)
    then
        raise exception using errcode = '22023', message = 'Invalid budget extra amount';
    end if;
    if p_month is null
       or extract(day from p_month) <> 1
       or p_month < date '2000-01-01'
       or p_month > current_month
    then
        raise exception using errcode = '22023', message = 'Invalid budget extra month';
    end if;
    if normalized_note is not null and char_length(normalized_note) > 240 then
        raise exception using errcode = '22023', message = 'Budget extra note is too long';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(caller_user_id::text || ':extra:' || p_request_id::text, 0)
    );

    select x.* into existing
    from public.budget_extras as x
    where x.user_id = caller_user_id and x.client_request_id = p_request_id;

    if found then
        if existing.amount is distinct from p_amount::numeric(10,2)
           or existing.note is distinct from normalized_note
           or existing.month is distinct from p_month
        then
            raise exception using errcode = '22023', message = 'Request id reused with different extra data';
        end if;
        return next existing;
        return;
    end if;

    insert into public.budget_extras (
        user_id, amount, note, month, client_request_id
    ) values (
        caller_user_id, p_amount, normalized_note, p_month, p_request_id
    ) returning * into inserted;

    return next inserted;
end;
$$;

revoke execute on function public.create_budget_extra(uuid, numeric, text, date)
    from public, anon;
grant execute on function public.create_budget_extra(uuid, numeric, text, date)
    to authenticated;

-- ============================================================
-- SOFT-DELETE GOALS AND PRESERVE THEIR LEDGER
-- ============================================================

create or replace function public.protect_archived_goal_financial_state()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if old.deleted_at is not null
       and (
           new.current_amount is distinct from old.current_amount
           or new.status is distinct from old.status
           or new.completed_at is distinct from old.completed_at
           or new.target_amount is distinct from old.target_amount
       )
    then
        raise exception using errcode = '55000', message = 'Archived goals are immutable';
    end if;
    return new;
end;
$$;

drop trigger if exists goals_protect_archived_state on public.goals;
create trigger goals_protect_archived_state
    before update on public.goals
    for each row execute function public.protect_archived_goal_financial_state();

revoke execute on function public.protect_archived_goal_financial_state()
    from public, anon, authenticated, service_role;

create or replace function public.archive_goal(p_goal_id uuid)
returns table (archived boolean)
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    changed integer;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;

    update public.goals as g
    set deleted_at = coalesce(g.deleted_at, pg_catalog.now())
    where g.id = p_goal_id and g.user_id = caller_user_id;
    get diagnostics changed = row_count;

    return query select changed > 0;
end;
$$;

revoke execute on function public.archive_goal(uuid) from public, anon;
grant execute on function public.archive_goal(uuid) to authenticated;

create or replace function public.reject_deposit_for_archived_goal()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if exists (
        select 1 from public.goals as g
        where g.id = new.goal_id and g.deleted_at is not null
    ) then
        raise exception using errcode = '55000', message = 'Archived goals cannot receive deposits';
    end if;
    return new;
end;
$$;

drop trigger if exists goal_deposits_reject_archived_goal on public.goal_deposits;
create trigger goal_deposits_reject_archived_goal
    before insert or update of goal_id on public.goal_deposits
    for each row execute function public.reject_deposit_for_archived_goal();

revoke execute on function public.reject_deposit_for_archived_goal()
    from public, anon, authenticated, service_role;

-- Reinstall the active-goal cap so archived goals do not occupy one of the five slots.
create or replace function public.enforce_active_goal_limit()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    active_count integer;
begin
    if new.user_id is null then
        raise exception using errcode = '23514', message = 'A goal must have an owner';
    end if;
    if auth.uid() is not null and not exists (
        select 1 from public.users as u
        where u.id = new.user_id and u.auth_id = auth.uid()
          and u.deletion_started_at is null
    ) then
        raise exception using errcode = '42501', message = 'Goal owner does not match caller';
    end if;

    if new.status = 'active'
       and new.deleted_at is null
       and (
           tg_op = 'INSERT'
           or old.status is distinct from 'active'
           or old.user_id is distinct from new.user_id
           or old.deleted_at is not null
       )
    then
        perform pg_advisory_xact_lock(hashtext(new.user_id::text));
        select count(*) into active_count
        from public.goals as g
        where g.user_id = new.user_id
          and g.status = 'active'
          and g.deleted_at is null
          and g.id is distinct from new.id;
        if active_count >= 5 then
            raise exception using errcode = '23514', message = 'A user can have at most five active goals';
        end if;
    end if;
    return new;
end;
$$;

drop trigger if exists goals_enforce_active_limit on public.goals;
create trigger goals_enforce_active_limit
    before insert or update of user_id, status, deleted_at on public.goals
    for each row execute function public.enforce_active_goal_limit();

create or replace view public.goals_with_deposit_count
with (security_invoker = on) as
select
    g.id,
    g.user_id,
    g.name,
    g.target_amount,
    g.current_amount,
    g.deadline,
    g.status,
    g.created_at,
    g.completed_at,
    count(d.id)::integer as deposit_count
from public.goals as g
left join public.goal_deposits as d
  on d.goal_id = g.id and d.user_id = g.user_id
where g.deleted_at is null
group by g.id;

alter view public.goals_with_deposit_count set (security_invoker = on);
grant select on public.goals_with_deposit_count to authenticated;

-- ============================================================
-- ATOMIC BADGE UNLOCKS AND REACHABLE LEVELS
-- ============================================================

-- Keep one authoritative implementation of every unlock criterion. The trigger
-- below uses it to reject forged table writes, while unlock_badge() can treat an
-- unmet (but otherwise valid) candidate as the normal `unlocked = false` result.
create or replace function public.badge_criterion_met(
    p_user_id uuid,
    p_badge_type text
)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    lima_today date := pg_catalog.timezone('America/Lima', pg_catalog.now())::date;
    month_start date := (
        pg_catalog.date_trunc(
            'month',
            pg_catalog.timezone('America/Lima', pg_catalog.now())
        ) - interval '1 month'
    )::date;
    month_end date;
    tracked_days integer;
    effective_budget numeric;
    spent numeric;
    criterion_met boolean := false;
begin
    if p_user_id is null or p_badge_type is null then
        return false;
    end if;

    case p_badge_type
        when 'first_expense' then
            select exists (
                select 1
                from public.expenses as e
                where e.user_id = p_user_id
            ) into criterion_met;

        when 'streak_7' then
            select count(distinct e.expense_date) = 7
            into criterion_met
            from public.expenses as e
            where e.user_id = p_user_id
              and e.expense_date between lima_today - 6 and lima_today;

        when 'streak_30' then
            select count(distinct e.expense_date) = 30
            into criterion_met
            from public.expenses as e
            where e.user_id = p_user_id
              and e.expense_date between lima_today - 29 and lima_today;

        when 'first_goal' then
            -- Logical archival must not erase an achievement already earned.
            select exists (
                select 1
                from public.goals as g
                where g.user_id = p_user_id
                  and g.status = 'completed'
            ) into criterion_met;

        when 'mil_soles' then
            -- Lifetime savings include active, completed and logically archived goals.
            select coalesce(sum(g.current_amount), 0) >= 1000
            into criterion_met
            from public.goals as g
            where g.user_id = p_user_id;

        when 'no_yape' then
            -- The historical wire name means a complete seven-day tracking window
            -- without an OCR-captured expense; `source` cannot identify payment rails.
            select count(distinct e.expense_date),
                   count(*) filter (where e.source = 'ocr') = 0
            into tracked_days, criterion_met
            from public.expenses as e
            where e.user_id = p_user_id
              and e.expense_date between lima_today - 6 and lima_today;
            criterion_met := tracked_days = 7 and criterion_met;

        when 'saver_month', 'perfect_month' then
            -- The schema retains one base budget, so the authoritative catch-up
            -- window is the most recently closed Lima calendar month. At least one
            -- tracked day is required: an empty month is not an achievement.
            month_end := (month_start + interval '1 month - 1 day')::date;

            select u.monthly_budget + coalesce(sum(x.amount), 0)
            into effective_budget
            from public.users as u
            left join public.budget_extras as x
              on x.user_id = u.id and x.month = month_start
            where u.id = p_user_id
            group by u.id, u.monthly_budget;

            select coalesce(sum(e.amount), 0), count(distinct e.expense_date)
            into spent, tracked_days
            from public.expenses as e
            where e.user_id = p_user_id
              and e.expense_date between month_start and month_end;

            criterion_met := effective_budget > 0
                and tracked_days > 0
                and spent <= effective_budget;
            if p_badge_type = 'perfect_month' then
                criterion_met := criterion_met
                    and tracked_days = pg_catalog.date_part('day', month_end)::integer;
            end if;

        else
            criterion_met := false;
    end case;

    return coalesce(criterion_met, false);
end;
$$;

revoke execute on function public.badge_criterion_met(uuid, text)
    from public, anon, authenticated, service_role;

-- Direct writes still fail closed, including writes by a privileged companion
-- service. API clients use unlock_badge(), which evaluates the same predicate first.
create or replace function public.validate_badge_unlock()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'UPDATE'
       and new.user_id is not distinct from old.user_id
       and new.badge_type is not distinct from old.badge_type
    then
        return new;
    end if;

    if auth.uid() is not null and not exists (
        select 1
        from public.users as u
        where u.id = new.user_id and u.auth_id = auth.uid()
    ) then
        raise exception using errcode = '42501', message = 'Badge owner does not match caller';
    end if;

    if not public.badge_criterion_met(new.user_id, new.badge_type) then
        raise exception using
            errcode = '23514',
            message = pg_catalog.format(
                'Badge criterion is not satisfied: %s',
                new.badge_type
            );
    end if;

    return new;
end;
$$;

revoke execute on function public.validate_badge_unlock()
    from public, anon, authenticated, service_role;

create or replace function public.unlock_badge(p_badge_type text)
returns table (unlocked boolean)
language plpgsql
security definer
set search_path = ''
as $$
declare
    caller_user_id uuid;
    badge_name text;
    inserted_count integer;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Active profile not found';
    end if;

    badge_name := case p_badge_type
        when 'first_expense' then 'Primer gasto'
        when 'streak_7' then '7 días seguidos'
        when 'streak_30' then '30 días'
        when 'first_goal' then 'Primera meta'
        when 'saver_month' then 'Bajo el presupuesto'
        when 'mil_soles' then 'Mil ahorrados'
        -- Historical wire name retained for compatibility; source='ocr' identifies
        -- capture method, not the user's payment rail.
        when 'no_yape' then 'Semana manual'
        when 'perfect_month' then 'Mes perfecto'
        else null
    end;
    if badge_name is null then
        raise exception using errcode = '22023', message = 'Unknown badge type';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended(caller_user_id::text || ':badge:' || p_badge_type, 0)
    );
    if exists (
        select 1 from public.badges as b
        where b.user_id = caller_user_id and b.badge_type = p_badge_type
    ) then
        return query select false;
        return;
    end if;

    if not public.badge_criterion_met(caller_user_id, p_badge_type) then
        return query select false;
        return;
    end if;

    insert into public.badges (user_id, badge_type, name)
    values (caller_user_id, p_badge_type, badge_name)
    on conflict (user_id, badge_type) do nothing;
    get diagnostics inserted_count = row_count;
    return query select inserted_count = 1;
end;
$$;

revoke execute on function public.unlock_badge(text) from public, anon;
grant execute on function public.unlock_badge(text) to authenticated;

create or replace function public.derive_user_gamification()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    derived_points integer;
begin
    select coalesce(sum(public.badge_points(b.badge_type)), 0)::integer
    into derived_points
    from public.badges as b
    where b.user_id = new.id;

    new.total_points := derived_points;
    new.level := case
        when derived_points >= 140 then 5
        when derived_points >= 100 then 4
        when derived_points >= 50 then 3
        when derived_points >= 20 then 2
        else 1
    end;
    return new;
end;
$$;

alter table public.users disable trigger users_touch_updated_at;
update public.users set total_points = total_points;
alter table public.users enable trigger users_touch_updated_at;

-- ============================================================
-- SERVER-DERIVED, UNBOUNDED CURRENT STREAK
-- ============================================================

create or replace function public.current_expense_streak()
returns table (streak integer)
language plpgsql
security definer
stable
set search_path = ''
as $$
declare
    caller_user_id uuid;
    lima_today date := pg_catalog.timezone('America/Lima', pg_catalog.now())::date;
    result integer;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;
    select u.id into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid() and u.deletion_started_at is null;
    if caller_user_id is null then
        return query select 0;
        return;
    end if;

    with distinct_days as (
        select distinct e.expense_date
        from public.expenses as e
        where e.user_id = caller_user_id
          and e.expense_date <= lima_today
    ), numbered as (
        select d.expense_date,
               row_number() over (order by d.expense_date desc) as position
        from distinct_days as d
    )
    select count(*)::integer into result
    from numbered as n
    where n.expense_date = lima_today - (n.position::integer - 1);

    return query select coalesce(result, 0);
end;
$$;

revoke execute on function public.current_expense_streak() from public, anon;
grant execute on function public.current_expense_streak() to authenticated;

-- ============================================================
-- CATEGORY SUMMARY MUST RETAIN UNCATEGORIZED EXPENSES
-- ============================================================

create or replace view public.monthly_category_summary
with (security_invoker = on) as
select
    e.user_id,
    date_trunc('month', e.expense_date::timestamp) as month,
    e.category_id,
    coalesce(c.name, 'Sin categoría') as category_name,
    coalesce(c.color, '#78909C') as category_color,
    sum(e.amount) as total,
    count(*) as transaction_count
from public.expenses as e
left join public.categories as c
  on c.id = e.category_id and c.user_id = e.user_id
group by
    e.user_id,
    date_trunc('month', e.expense_date::timestamp),
    e.category_id,
    coalesce(c.name, 'Sin categoría'),
    coalesce(c.color, '#78909C');

alter view public.monthly_category_summary set (security_invoker = on);
grant select on public.monthly_category_summary to authenticated;

-- ============================================================
-- GLOBAL CHALLENGE RETENTION HOOK
-- ============================================================

-- 0006 already indexes active rows by expires_at. Add the complementary compact
-- index for the terminal audit window, then express cleanup as the same two partial
-- predicates so neither path needs an OR scan or an index containing every row.
create index whatsapp_link_challenges_terminal_created
    on public.whatsapp_link_challenges (created_at)
    where consumed_at is not null or invalidated_at is not null;

create or replace function public.purge_expired_whatsapp_link_challenges()
returns bigint
language plpgsql
security definer
set search_path = ''
as $$
declare
    retention_cutoff timestamptz := pg_catalog.now() - interval '24 hours';
    expired_removed bigint;
    terminal_removed bigint;
begin
    delete from public.whatsapp_link_challenges as c
    where c.consumed_at is null
      and c.invalidated_at is null
      and c.expires_at < retention_cutoff;
    get diagnostics expired_removed = row_count;

    -- expires_at > created_at is a table constraint, so every terminal row whose
    -- expiry is older than the cutoff is necessarily covered by this audit-window
    -- predicate. Together the two deletes are equivalent to the former OR.
    delete from public.whatsapp_link_challenges as c
    where (c.consumed_at is not null or c.invalidated_at is not null)
      and c.created_at < retention_cutoff;
    get diagnostics terminal_removed = row_count;

    return expired_removed + terminal_removed;
end;
$$;

create or replace function public.purge_whatsapp_challenges_before_insert()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    perform public.purge_expired_whatsapp_link_challenges();
    return null;
end;
$$;

drop trigger if exists whatsapp_challenges_global_retention
    on public.whatsapp_link_challenges;
create trigger whatsapp_challenges_global_retention
    before insert on public.whatsapp_link_challenges
    for each statement execute function public.purge_whatsapp_challenges_before_insert();

revoke execute on function public.purge_expired_whatsapp_link_challenges()
    from public, anon, authenticated;
grant execute on function public.purge_expired_whatsapp_link_challenges()
    to service_role;
revoke execute on function public.purge_whatsapp_challenges_before_insert()
    from public, anon, authenticated, service_role;
