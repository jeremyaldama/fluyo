-- 0006: production data integrity and trusted operations.
--
-- This is the EXPAND phase of the goal-deposit rollout: it adds the new columns,
-- view and RPC while legacy clients can still use the old write path. Deploy the
-- compatible Android client before applying the contract migration 0008.
-- Constraints that could be violated by
-- legacy rows are installed NOT VALID: PostgreSQL enforces them for every new or
-- changed row without deleting or rewriting historical data. After auditing any
-- legacy violations, operators can VALIDATE each constraint in a later migration.

-- 0001 historically let PostgreSQL choose pgcrypto's schema. Hosted Supabase uses
-- `extensions`, while vanilla PostgreSQL commonly chose `public`. pgcrypto is
-- relocatable, so normalize both fresh and upgraded installations before using
-- schema-qualified cryptographic functions in SECURITY DEFINER routines.
create schema if not exists extensions;
do $migration$
declare
    installed_schema text;
begin
    select n.nspname
    into installed_schema
    from pg_catalog.pg_extension as e
    join pg_catalog.pg_namespace as n on n.oid = e.extnamespace
    where e.extname = 'pgcrypto';

    if installed_schema is null then
        execute 'create extension pgcrypto with schema extensions';
    elsif installed_schema <> 'extensions' then
        execute 'alter extension pgcrypto set schema extensions';
    end if;
end;
$migration$;

-- Supabase roles never need to create arbitrary objects in the API schema.
-- Removing the PostgreSQL default closes search-path object-shadowing attacks
-- against SECURITY DEFINER functions added below.
revoke create on schema public from public, anon, authenticated, service_role;

-- ============================================================
-- BASIC RANGES AND REQUIRED OWNERSHIP
-- ============================================================

alter table public.categories
    add constraint categories_user_required
        check (user_id is not null) not valid,
    add constraint categories_name_valid
        check (char_length(btrim(name)) between 1 and 80) not valid,
    add constraint categories_icon_valid
        check (
            icon = btrim(icon)
            and char_length(icon) between 1 and 40
            and icon ~ '^[a-z0-9_-]+$'
        ) not valid,
    add constraint categories_color_valid
        check (color ~ '^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$') not valid,
    add constraint categories_display_order_valid
        check (display_order is not null and display_order >= 0) not valid,
    add constraint categories_id_user_unique unique (id, user_id);

alter table public.expenses
    add constraint expenses_user_required
        check (user_id is not null) not valid,
    add constraint expenses_amount_valid
        check (
            amount > 0
            and amount <= 99999999.99
            and amount::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint expenses_date_required
        check (expense_date is not null) not valid,
    add constraint expenses_description_length_valid
        check (description is null or char_length(description) <= 500) not valid,
    add constraint expenses_recipient_length_valid
        check (recipient is null or char_length(recipient) <= 160) not valid,
    add constraint expenses_image_url_valid
        check (
            image_url is null
            or (
                char_length(image_url) between 1 and 2048
                and image_url ~ '^(https://|content://)'
            )
        ) not valid,
    add constraint expenses_category_owned_by_user
        foreign key (category_id, user_id)
        references public.categories (id, user_id)
        not valid;

alter table public.goals
    add constraint goals_user_required
        check (user_id is not null) not valid,
    add constraint goals_name_valid
        check (char_length(btrim(name)) between 1 and 120) not valid,
    add constraint goals_target_amount_valid
        check (
            target_amount > 0
            and target_amount <= 99999999.99
            and target_amount::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint goals_current_amount_valid
        check (
            current_amount is not null
            and current_amount >= 0
            and current_amount <= 99999999.99
            and current_amount::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint goals_completion_state_valid
        check (
            status is not null
            and (
                (status = 'active' and completed_at is null)
                or
                (status = 'completed' and completed_at is not null and current_amount >= target_amount)
            )
        ) not valid,
    add constraint goals_id_user_unique unique (id, user_id);

alter table public.badges
    add constraint badges_user_required
        check (user_id is not null) not valid,
    add constraint badges_name_valid
        check (
            name = btrim(name)
            and char_length(name) between 1 and 100
        ) not valid,
    add constraint badges_description_length_valid
        check (description is null or char_length(description) <= 500) not valid,
    add constraint badges_criteria_length_valid
        check (criteria is null or char_length(criteria) <= 500) not valid,
    add constraint badges_type_known
        check (
            badge_type in (
                'first_expense', 'streak_7', 'saver_month', 'first_goal',
                'streak_30', 'mil_soles', 'no_yape', 'perfect_month'
            )
        ) not valid;

alter table public.goal_deposits
    add column if not exists request_id uuid,
    add column if not exists balance_after numeric(10,2),
    add column if not exists completed_goal boolean,
    add column if not exists deposit_count_after bigint,
    add constraint goal_deposits_user_required
        check (user_id is not null) not valid,
    add constraint goal_deposits_goal_required
        check (goal_id is not null) not valid,
    add constraint goal_deposits_amount_valid
        check (
            amount > 0
            and amount <= 99999999.99
            and amount::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint goal_deposits_goal_owned_by_user
        foreign key (goal_id, user_id)
        references public.goals (id, user_id)
        not valid;

create unique index if not exists goal_deposits_user_request_unique
    on public.goal_deposits (user_id, request_id)
    where request_id is not null;

alter table public.budget_extras
    add constraint budget_extras_user_required
        check (user_id is not null) not valid,
    add constraint budget_extras_amount_valid
        check (
            amount > 0
            and amount <= 99999999.99
            and amount::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint budget_extras_note_length_valid
        check (note is null or char_length(note) <= 240) not valid;

-- Currency changes and the first monetary write must serialize on the same key.
-- Without this, a currency UPDATE can observe no committed activity while a concurrent
-- INSERT is still in flight, allowing both transactions to commit and relabel that first
-- movement. Ownership transfers lock both users in hash order to avoid lock inversion.
create or replace function public.lock_monetary_owner()
returns trigger
language plpgsql
security definer
set search_path = pg_catalog, public, pg_temp
as $$
declare
    previous_lock_key integer;
    next_lock_key integer;
begin
    if new.user_id is null then
        return new;
    end if;

    next_lock_key := hashtext(new.user_id::text);
    if tg_op = 'UPDATE' and old.user_id is distinct from new.user_id and old.user_id is not null then
        previous_lock_key := hashtext(old.user_id::text);
        perform pg_advisory_xact_lock(least(previous_lock_key, next_lock_key)::bigint);
        if previous_lock_key <> next_lock_key then
            perform pg_advisory_xact_lock(greatest(previous_lock_key, next_lock_key)::bigint);
        end if;
    else
        perform pg_advisory_xact_lock(next_lock_key::bigint);
    end if;

    return new;
end;
$$;

drop trigger if exists expenses_lock_monetary_owner on public.expenses;
create trigger expenses_lock_monetary_owner
    before insert or update of user_id on public.expenses
    for each row execute function public.lock_monetary_owner();

-- Sort before goals_enforce_active_limit: transfers acquire old/new locks in a stable
-- order, then the active-limit trigger harmlessly re-enters the new owner's lock.
drop trigger if exists goals_00_lock_monetary_owner on public.goals;
create trigger goals_00_lock_monetary_owner
    before insert or update of user_id on public.goals
    for each row execute function public.lock_monetary_owner();

drop trigger if exists budget_extras_lock_monetary_owner on public.budget_extras;
create trigger budget_extras_lock_monetary_owner
    before insert or update of user_id on public.budget_extras
    for each row execute function public.lock_monetary_owner();

revoke execute on function public.lock_monetary_owner() from public, anon, authenticated;

-- Currency is a denomination, not a display preference. Because historical rows do not
-- yet carry their own currency/rate, changing this value after monetary activity would
-- silently relabel every amount. Empty accounts can still choose their initial currency.
create or replace function public.prevent_currency_relabeling()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    perform pg_advisory_xact_lock(hashtext(old.id::text)::bigint);

    if new.currency is distinct from old.currency
        and (
            exists (select 1 from public.expenses as e where e.user_id = old.id)
            or exists (select 1 from public.goals as g where g.user_id = old.id)
            or exists (select 1 from public.budget_extras as x where x.user_id = old.id)
        )
    then
        raise exception using
            errcode = '23514',
            message = 'Currency cannot change after monetary activity exists';
    end if;
    return new;
end;
$$;

drop trigger if exists users_prevent_currency_relabeling on public.users;
create trigger users_prevent_currency_relabeling
    before update of currency on public.users
    for each row execute function public.prevent_currency_relabeling();

revoke execute on function public.prevent_currency_relabeling() from public, anon, authenticated;

-- ============================================================
-- BUSINESS RULE: AT MOST FIVE ACTIVE GOALS PER USER
-- The advisory transaction lock serializes concurrent inserts/reactivations for
-- the same user. Existing users above the cap retain their rows but cannot add more.
-- ============================================================

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
        raise exception using
            errcode = '23514',
            message = 'A goal must have an owner';
    end if;

    if auth.uid() is not null and not exists (
        select 1
        from public.users as u
        where u.id = new.user_id and u.auth_id = auth.uid()
    ) then
        raise exception using errcode = '42501', message = 'Goal owner does not match caller';
    end if;

    if new.status = 'active'
        and (
            tg_op = 'INSERT'
            or old.status is distinct from 'active'
            or old.user_id is distinct from new.user_id
        )
    then
        perform pg_advisory_xact_lock(hashtext(new.user_id::text));

        select count(*)
        into active_count
        from public.goals as g
        where g.user_id = new.user_id
          and g.status = 'active'
          and g.id is distinct from new.id;

        if active_count >= 5 then
            raise exception using
                errcode = '23514',
                message = 'A user can have at most five active goals';
        end if;
    end if;

    return new;
end;
$$;

drop trigger if exists goals_enforce_active_limit on public.goals;
create trigger goals_enforce_active_limit
    before insert or update of user_id, status on public.goals
    for each row execute function public.enforce_active_goal_limit();

revoke execute on function public.enforce_active_goal_limit() from public, anon, authenticated;

-- ============================================================
-- GAMIFICATION IS DERIVED, NOT CLIENT-ASSIGNED
-- ============================================================

create or replace function public.badge_points(p_badge_type text)
returns integer
language sql
immutable
strict
set search_path = public, pg_temp
as $$
    select case p_badge_type
        when 'first_expense' then 1
        when 'streak_7' then 5
        when 'saver_month' then 15
        when 'first_goal' then 10
        when 'streak_30' then 20
        when 'mil_soles' then 25
        when 'no_yape' then 15
        when 'perfect_month' then 50
        else 0
    end;
$$;

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
        when derived_points >= 200 then 5
        when derived_points >= 100 then 4
        when derived_points >= 50 then 3
        when derived_points >= 20 then 2
        else 1
    end;
    return new;
end;
$$;

drop trigger if exists users_derive_gamification on public.users;
create trigger users_derive_gamification
    before insert or update on public.users
    for each row execute function public.derive_user_gamification();

-- Reject a forged badge unless its database-backed criterion currently holds.
-- This mirrors the Android rules while making direct Data API inserts non-authoritative.
create or replace function public.validate_badge_unlock()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    lima_today date := timezone('America/Lima', now())::date;
    -- Monthly badges are catch-up checks over the most recently closed month. Requiring
    -- the app to run on the exact last day made achievements permanently missable.
    month_start date := (
        date_trunc('month', timezone('America/Lima', now())) - interval '1 month'
    )::date;
    month_end date;
    tracked_days integer;
    effective_budget numeric;
    spent numeric;
    criterion_met boolean := false;
begin
    if tg_op = 'UPDATE' then
        if new.user_id is not distinct from old.user_id
            and new.badge_type is not distinct from old.badge_type
        then
            return new;
        end if;
    end if;

    if auth.uid() is not null and not exists (
        select 1
        from public.users as u
        where u.id = new.user_id and u.auth_id = auth.uid()
    ) then
        raise exception using errcode = '42501', message = 'Badge owner does not match caller';
    end if;

    case new.badge_type
        when 'first_expense' then
            select exists (
                select 1 from public.expenses as e where e.user_id = new.user_id
            ) into criterion_met;

        when 'streak_7' then
            select count(distinct e.expense_date) = 7
            into criterion_met
            from public.expenses as e
            where e.user_id = new.user_id
              and e.expense_date between lima_today - 6 and lima_today;

        when 'streak_30' then
            select count(distinct e.expense_date) = 30
            into criterion_met
            from public.expenses as e
            where e.user_id = new.user_id
              and e.expense_date between lima_today - 29 and lima_today;

        when 'first_goal' then
            select exists (
                select 1
                from public.goals as g
                where g.user_id = new.user_id and g.status = 'completed'
            ) into criterion_met;

        when 'mil_soles' then
            select coalesce(sum(g.current_amount), 0) >= 1000
            into criterion_met
            from public.goals as g
            where g.user_id = new.user_id;

        when 'no_yape' then
            select count(distinct e.expense_date),
                   count(*) filter (where e.source = 'ocr') = 0
            into tracked_days, criterion_met
            from public.expenses as e
            where e.user_id = new.user_id
              and e.expense_date between lima_today - 6 and lima_today;
            criterion_met := tracked_days = 7 and criterion_met;

        when 'saver_month', 'perfect_month' then
            month_end := (month_start + interval '1 month - 1 day')::date;
            select u.monthly_budget + coalesce(sum(x.amount), 0)
            into effective_budget
            from public.users as u
            left join public.budget_extras as x
              on x.user_id = u.id and x.month = month_start
            where u.id = new.user_id
            group by u.id, u.monthly_budget;

            select coalesce(sum(e.amount), 0), count(distinct e.expense_date)
            into spent, tracked_days
            from public.expenses as e
            where e.user_id = new.user_id
              and e.expense_date between month_start and month_end;

            criterion_met := effective_budget > 0 and spent <= effective_budget;
            if new.badge_type = 'perfect_month' then
                criterion_met := criterion_met and tracked_days = extract(day from month_end)::integer;
            end if;

        else
            criterion_met := false;
    end case;

    if not coalesce(criterion_met, false) then
        raise exception using
            errcode = '23514',
            message = format('Badge criterion is not satisfied: %s', new.badge_type);
    end if;

    return new;
end;
$$;

drop trigger if exists badges_validate_unlock on public.badges;
create trigger badges_validate_unlock
    before insert or update of user_id, badge_type on public.badges
    for each row execute function public.validate_badge_unlock();

create or replace function public.refresh_user_gamification_after_badge()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if tg_op = 'DELETE' or tg_op = 'UPDATE' then
        update public.users
        set total_points = total_points
        where id = old.user_id;
    end if;

    if tg_op = 'INSERT' then
        update public.users
        set total_points = total_points
        where id = new.user_id;
    end if;

    if tg_op = 'UPDATE' and new.user_id is distinct from old.user_id then
        update public.users
        set total_points = total_points
        where id = new.user_id;
    end if;

    if tg_op = 'DELETE' then
        return old;
    end if;
    return new;
end;
$$;

drop trigger if exists badges_refresh_user_gamification on public.badges;
create trigger badges_refresh_user_gamification
    after insert or update or delete on public.badges
    for each row execute function public.refresh_user_gamification_after_badge();

revoke execute on function public.badge_points(text) from public, anon, authenticated;
revoke execute on function public.derive_user_gamification() from public, anon, authenticated;
revoke execute on function public.validate_badge_unlock() from public, anon, authenticated;
revoke execute on function public.refresh_user_gamification_after_badge() from public, anon, authenticated;

-- Recalculate the two derived columns without removing badge or user data. Install the
-- new NOT VALID user checks only *after* this one-time update: PostgreSQL enforces a
-- NOT VALID check on changed rows, so installing them first would make an unrelated
-- legacy value abort the entire migration. Preserve updated_at during the backfill.
alter table public.users disable trigger users_touch_updated_at;
update public.users set total_points = total_points;
alter table public.users enable trigger users_touch_updated_at;

alter table public.users
    add constraint users_auth_id_required
        check (auth_id is not null) not valid,
    add constraint users_email_format_valid
        check (
            email is null
            or (
                email = btrim(email)
                and char_length(email) between 3 and 254
                and email ~* '^[^[:space:]@]+@[^[:space:]@]+[.][^[:space:]@]+$'
            )
        ) not valid,
    add constraint users_display_name_valid
        check (
            display_name is null
            or (
                display_name = btrim(display_name)
                and char_length(display_name) between 1 and 100
            )
        ) not valid,
    add constraint users_legacy_phone_format_valid
        check (
            phone_number is null
            or phone_number ~ '^([+])?[1-9][0-9]{7,14}$'
        ) not valid,
    add constraint users_monthly_budget_valid
        check (
            monthly_budget is not null
            and monthly_budget >= 0
            and monthly_budget::text not in ('NaN', 'Infinity', '-Infinity')
        ) not valid,
    add constraint users_currency_supported
        check (currency is not null and currency in ('PEN', 'USD', 'EUR')) not valid,
    add constraint users_level_valid
        check (level is not null and level between 1 and 5) not valid,
    add constraint users_total_points_valid
        check (total_points is not null and total_points >= 0) not valid,
    add constraint users_notification_enabled_required
        check (notification_enabled is not null) not valid,
    add constraint users_notification_hour_valid
        check (notification_hour is not null and notification_hour between 0 and 23) not valid,
    add constraint users_notification_types_valid
        check (
            notification_types is not null
            and notification_types <@ array['progress', 'reminder', 'budget', 'goal']::text[]
        ) not valid;

-- Badges are append-only for mobile clients. INSERT remains available, guarded by
-- RLS and validate_badge_unlock; derived XP cannot be supplied by the client.
revoke update, delete on public.badges from public, anon, authenticated;

-- ============================================================
-- ATOMIC, IDEMPOTENT GOAL DEPOSITS
-- ============================================================

create or replace function public.deposit_to_goal(
    p_goal_id uuid,
    p_amount numeric,
    p_request_id uuid
)
returns table (
    id uuid,
    user_id uuid,
    name text,
    target_amount numeric,
    current_amount numeric,
    deadline date,
    status text,
    created_at timestamptz,
    completed_at timestamptz,
    deposit_count bigint,
    just_completed boolean
)
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    caller_user_id uuid;
    goal_row public.goals%rowtype;
    prior_deposit public.goal_deposits%rowtype;
    rounded_amount numeric(10,2);
    new_balance numeric;
    did_complete boolean;
    new_deposit_count bigint;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;

    select u.id
    into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid();

    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Authenticated profile not found';
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
        raise exception using
            errcode = '22023',
            message = 'Deposit amount must be positive and have at most two decimal places';
    end if;
    rounded_amount := p_amount::numeric(10,2);

    -- Serialize duplicate request ids even if a malicious caller changes goal_id.
    perform pg_advisory_xact_lock(
        hashtextextended(caller_user_id::text || ':' || p_request_id::text, 0)
    );

    select d.*
    into prior_deposit
    from public.goal_deposits as d
    where d.user_id = caller_user_id and d.request_id = p_request_id;

    if found then
        if prior_deposit.goal_id is distinct from p_goal_id
            or prior_deposit.amount is distinct from rounded_amount
        then
            raise exception using
                errcode = '22023',
                message = 'The request id was already used with different deposit data';
        end if;

        return query
        select g.id, g.user_id, g.name, g.target_amount, prior_deposit.balance_after,
               g.deadline,
               case when prior_deposit.completed_goal then 'completed' else 'active' end,
               g.created_at,
               case when prior_deposit.completed_goal then g.completed_at else null end,
               prior_deposit.deposit_count_after,
               coalesce(prior_deposit.completed_goal, false)
        from public.goals as g
        where g.id = prior_deposit.goal_id and g.user_id = caller_user_id;
        return;
    end if;

    select g.*
    into goal_row
    from public.goals as g
    where g.id = p_goal_id and g.user_id = caller_user_id
    for update;

    if not found then
        -- Do not reveal whether another user's UUID exists.
        raise exception using
            errcode = '42501',
            message = 'Goal not found or not owned by the authenticated user';
    end if;

    if goal_row.current_amount is null
        or goal_row.target_amount is null
        or goal_row.status is null
    then
        raise exception using
            errcode = '23514',
            message = 'Goal has invalid legacy monetary state and must be repaired before depositing';
    end if;

    if goal_row.status <> 'active' then
        raise exception using errcode = '23514', message = 'Completed goals cannot receive deposits';
    end if;

    new_balance := goal_row.current_amount + rounded_amount;
    if new_balance > 99999999.99 then
        raise exception using errcode = '22003', message = 'Goal balance exceeds the supported range';
    end if;

    did_complete := new_balance >= goal_row.target_amount;

    select count(*) + 1
    into new_deposit_count
    from public.goal_deposits as d
    where d.goal_id = goal_row.id;

    update public.goals as g
    set current_amount = new_balance::numeric(10,2),
        status = case when did_complete then 'completed' else g.status end,
        completed_at = case when did_complete then now() else g.completed_at end
    where g.id = goal_row.id
    returning g.* into goal_row;

    insert into public.goal_deposits (
        goal_id, user_id, amount, request_id, balance_after, completed_goal,
        deposit_count_after
    ) values (
        goal_row.id, caller_user_id, rounded_amount, p_request_id,
        goal_row.current_amount, did_complete, new_deposit_count
    );

    return query
    select goal_row.id, goal_row.user_id, goal_row.name, goal_row.target_amount,
           goal_row.current_amount, goal_row.deadline, goal_row.status,
           goal_row.created_at, goal_row.completed_at,
           new_deposit_count,
           did_complete;
end;
$$;

revoke execute on function public.deposit_to_goal(uuid, numeric, uuid) from public, anon;
grant execute on function public.deposit_to_goal(uuid, numeric, uuid) to authenticated;

-- Goal creation is still available to old and new clients, but callers cannot
-- forge a starting balance/completion state. The database defaults provide the
-- only allowed initial current_amount/status/completed_at values.
revoke insert on public.goals from public, anon, authenticated;
grant insert (user_id, name, target_amount, deadline) on public.goals to authenticated;

-- Direct legacy deposit/update privileges intentionally remain during this
-- expand phase. 0007 removes them only after RPC-capable clients are deployed.

-- A security-invoker view avoids downloading every deposit merely to count them.
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
group by g.id;

grant select on public.goals_with_deposit_count to authenticated;

-- ============================================================
-- LIMA-CONSISTENT CURRENT MONTH
-- ============================================================

-- Persisted monetary movements remain numeric(10,2), so every individual write is
-- capped at 99,999,999.99. Aggregates cannot share that typmod: even two valid rows
-- can exceed it, and PostgreSQL's sum(numeric) intentionally returns unconstrained
-- numeric. Recreate the view because CREATE OR REPLACE cannot change column typmods.
-- Column names/order and their JSON number representation stay unchanged for clients.
drop view if exists public.current_month_budget;
create view public.current_month_budget
with (security_invoker = on) as
with local_period as (
    select date_trunc('month', timezone('America/Lima', now()))::date as month_start
), expense_totals as (
    select e.user_id, sum(e.amount) as spent
    from public.expenses as e
    cross join local_period as p
    where e.expense_date >= p.month_start
      and e.expense_date < (p.month_start + interval '1 month')::date
    group by e.user_id
), extra_totals as (
    select x.user_id, sum(x.amount) as extra
    from public.budget_extras as x
    cross join local_period as p
    where x.month = p.month_start
    group by x.user_id
)
select
    u.id as user_id,
    (u.monthly_budget::numeric + coalesce(x.extra, 0::numeric))::numeric
        as monthly_budget,
    coalesce(e.spent, 0::numeric)::numeric as total_spent,
    (
        u.monthly_budget::numeric
        + coalesce(x.extra, 0::numeric)
        - coalesce(e.spent, 0::numeric)
    )::numeric as remaining,
    coalesce(x.extra, 0::numeric)::numeric as extra_income
from public.users as u
left join expense_totals as e on e.user_id = u.id
left join extra_totals as x on x.user_id = u.id;

alter view public.current_month_budget set (security_invoker = on);
grant select on public.current_month_budget to authenticated, service_role;

-- ============================================================
-- VERIFIED WHATSAPP IDENTITY (BACKEND-OWNED)
-- ============================================================

create table if not exists public.whatsapp_links (
    user_id uuid primary key references public.users(id) on delete cascade,
    phone_e164 text not null unique,
    verified_at timestamptz not null,
    verification_method text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint whatsapp_links_phone_e164_valid
        check (phone_e164 ~ '^\+[1-9][0-9]{7,14}$'),
    constraint whatsapp_links_verification_method_valid
        check (verification_method in ('sender_challenge', 'manual_admin'))
);

comment on table public.whatsapp_links is
    'Canonical WhatsApp identities verified out-of-band. Only a trusted backend may write rows.';
comment on column public.users.phone_number is
    'Legacy unverified profile input. Never use for WhatsApp routing; use whatsapp_links.phone_e164.';

drop trigger if exists whatsapp_links_touch_updated_at on public.whatsapp_links;
create trigger whatsapp_links_touch_updated_at
    before update on public.whatsapp_links
    for each row execute function public.touch_updated_at();

alter table public.whatsapp_links enable row level security;

drop policy if exists whatsapp_links_select_own on public.whatsapp_links;
create policy whatsapp_links_select_own
    on public.whatsapp_links
    for select
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- There is intentionally no INSERT/UPDATE/DELETE policy for mobile clients. The absent
-- WhatsApp backend must verify a single-use sender challenge before writing this table
-- with service_role; merely typing a number in the app never creates a verified link.
revoke all on public.whatsapp_links from public, anon, authenticated;
grant select on public.whatsapp_links to authenticated;
grant all on public.whatsapp_links to service_role;

-- A challenge contains no phone number. The authenticated app receives a random token
-- and the user sends it through WhatsApp; only the trusted webhook backend can pair the
-- token with the E.164 sender observed by WhatsApp.
create table if not exists public.whatsapp_link_challenges (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.users(id) on delete cascade,
    token_hash bytea not null unique,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    invalidated_at timestamptz,
    created_at timestamptz not null default now(),
    constraint whatsapp_link_challenges_hash_valid
        check (octet_length(token_hash) = 32),
    constraint whatsapp_link_challenges_expiry_valid
        check (expires_at > created_at),
    constraint whatsapp_link_challenges_terminal_state_valid
        check (consumed_at is null or invalidated_at is null)
);

create unique index if not exists whatsapp_link_challenges_one_active_per_user
    on public.whatsapp_link_challenges (user_id)
    where consumed_at is null and invalidated_at is null;

create index if not exists whatsapp_link_challenges_expiry
    on public.whatsapp_link_challenges (expires_at)
    where consumed_at is null and invalidated_at is null;

create index if not exists whatsapp_link_challenges_user_created
    on public.whatsapp_link_challenges (user_id, created_at desc);

alter table public.whatsapp_link_challenges enable row level security;
revoke all on public.whatsapp_link_challenges from public, anon, authenticated;
grant all on public.whatsapp_link_challenges to service_role;

create or replace function public.create_whatsapp_link_challenge()
returns table (
    token text,
    expires_at timestamptz
)
language plpgsql
security definer
set search_path = pg_catalog, extensions, public, pg_temp
as $$
declare
    caller_user_id uuid;
    raw_token text;
    challenge_expiry timestamptz := now() + interval '10 minutes';
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;

    select u.id
    into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid();

    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Authenticated profile not found';
    end if;

    perform pg_advisory_xact_lock(hashtext(caller_user_id::text));

    if exists (
        select 1
        from public.whatsapp_link_challenges as c
        where c.user_id = caller_user_id
          and c.created_at > now() - interval '60 seconds'
    ) then
        raise exception using
            errcode = '55000',
            message = 'Wait before requesting another WhatsApp link challenge';
    end if;

    update public.whatsapp_link_challenges as c
    set invalidated_at = now()
    where c.user_id = caller_user_id
      and c.consumed_at is null
      and c.invalidated_at is null;

    -- Keep a short audit window without allowing unbounded growth from repeated
    -- authenticated requests. The advisory lock serializes cleanup and creation.
    delete from public.whatsapp_link_challenges as c
    where c.user_id = caller_user_id
      and (c.consumed_at is not null or c.invalidated_at is not null)
      and c.created_at < now() - interval '24 hours';

    raw_token := encode(extensions.gen_random_bytes(16), 'hex');

    insert into public.whatsapp_link_challenges (user_id, token_hash, expires_at)
    values (caller_user_id, extensions.digest(raw_token, 'sha256'), challenge_expiry);

    return query select raw_token, challenge_expiry;
end;
$$;

revoke execute on function public.create_whatsapp_link_challenge() from public, anon;
grant execute on function public.create_whatsapp_link_challenge() to authenticated;

-- This function does not inspect or authenticate a WhatsApp webhook. Its restricted
-- caller must supply the sender number obtained from a verified Meta webhook event.
create or replace function public.confirm_whatsapp_link_challenge(
    p_token text,
    p_phone_e164 text
)
returns table (
    user_id uuid,
    phone_e164 text,
    verified_at timestamptz
)
language plpgsql
security definer
set search_path = pg_catalog, extensions, public, pg_temp
as $$
declare
    challenge_row public.whatsapp_link_challenges%rowtype;
    verification_time timestamptz := now();
begin
    if p_token is null or p_token !~ '^[0-9a-f]{32}$' then
        raise exception using errcode = '22023', message = 'Invalid challenge token';
    end if;
    if p_phone_e164 is null or p_phone_e164 !~ '^\+[1-9][0-9]{7,14}$' then
        raise exception using errcode = '22023', message = 'Phone must be canonical E.164';
    end if;

    select c.*
    into challenge_row
    from public.whatsapp_link_challenges as c
    where c.token_hash = extensions.digest(p_token, 'sha256')
      and c.consumed_at is null
      and c.invalidated_at is null
      and c.expires_at > verification_time
    for update;

    if not found then
        raise exception using
            errcode = '22023',
            message = 'Challenge is invalid, expired, or already consumed';
    end if;

    insert into public.whatsapp_links (
        user_id, phone_e164, verified_at, verification_method
    ) values (
        challenge_row.user_id, p_phone_e164, verification_time, 'sender_challenge'
    )
    on conflict on constraint whatsapp_links_pkey do update
    set phone_e164 = excluded.phone_e164,
        verified_at = excluded.verified_at,
        verification_method = excluded.verification_method,
        updated_at = verification_time;

    update public.whatsapp_link_challenges as c
    set consumed_at = verification_time
    where c.id = challenge_row.id;

    return query
    select l.user_id, l.phone_e164, l.verified_at
    from public.whatsapp_links as l
    where l.user_id = challenge_row.user_id;
end;
$$;

revoke execute on function public.confirm_whatsapp_link_challenge(text, text)
    from public, anon, authenticated;
grant execute on function public.confirm_whatsapp_link_challenge(text, text)
    to service_role;

-- Mobile users may revoke their own verified link without receiving write access
-- to the backend-owned identity table. Re-linking always requires a fresh sender
-- challenge, so revocation also invalidates every outstanding token.
create or replace function public.unlink_whatsapp_link()
returns table (unlinked boolean)
language plpgsql
security definer
set search_path = pg_catalog, public, pg_temp
as $$
declare
    caller_user_id uuid;
    removed_count integer;
begin
    if auth.uid() is null then
        raise exception using errcode = '28000', message = 'Authentication required';
    end if;

    select u.id
    into caller_user_id
    from public.users as u
    where u.auth_id = auth.uid();

    if caller_user_id is null then
        raise exception using errcode = '28000', message = 'Authenticated profile not found';
    end if;

    perform pg_advisory_xact_lock(hashtext(caller_user_id::text));

    update public.whatsapp_link_challenges as c
    set invalidated_at = now()
    where c.user_id = caller_user_id
      and c.consumed_at is null
      and c.invalidated_at is null;

    delete from public.whatsapp_links as l
    where l.user_id = caller_user_id;
    get diagnostics removed_count = row_count;

    return query select removed_count > 0;
end;
$$;

revoke execute on function public.unlink_whatsapp_link() from public, anon;
grant execute on function public.unlink_whatsapp_link() to authenticated;
