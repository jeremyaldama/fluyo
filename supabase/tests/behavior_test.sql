\set ON_ERROR_STOP on
begin;

-- pgcrypto must be relocated from vanilla PostgreSQL's public schema.
do $$
declare
    extension_schema text;
begin
    select n.nspname
    into extension_schema
    from pg_catalog.pg_extension as e
    join pg_catalog.pg_namespace as n on n.oid = e.extnamespace
    where e.extname = 'pgcrypto';

    if extension_schema is distinct from 'extensions' then
        raise exception 'pgcrypto remained in schema %', extension_schema;
    end if;
    if not exists (
        select 1
        from pg_catalog.pg_proc as p
        join pg_catalog.pg_namespace as n on n.oid = p.pronamespace
        where n.nspname = 'extensions' and p.proname = 'digest'
    ) then
        raise exception 'extensions.digest is missing';
    end if;
end;
$$;

-- 0006 must actively undo legacy CREATE privileges for API roles.
do $$
begin
    if has_schema_privilege('anon', 'public', 'CREATE')
        or has_schema_privilege('authenticated', 'public', 'CREATE')
        or has_schema_privilege('service_role', 'public', 'CREATE')
    then
        raise exception 'An API role still has CREATE on schema public';
    end if;
end;
$$;

-- Currency relabeling and every monetary-owner write must participate in the
-- same serialization contract. The separate two-session test exercises the race;
-- this catalog check prevents a table from silently dropping its side of the lock.
do $$
declare
    configured_triggers integer;
begin
    with expected (table_name, trigger_name) as (
        values
            ('expenses', 'expenses_lock_monetary_owner'),
            ('goals', 'goals_00_lock_monetary_owner'),
            ('budget_extras', 'budget_extras_lock_monetary_owner')
    )
    select count(*)
    into configured_triggers
    from expected as e
    join pg_catalog.pg_class as c
      on c.relname = e.table_name
    join pg_catalog.pg_namespace as n
      on n.oid = c.relnamespace and n.nspname = 'public'
    join pg_catalog.pg_trigger as t
      on t.tgrelid = c.oid
     and t.tgname = e.trigger_name
     and not t.tgisinternal
     and t.tgenabled = 'O'
    join pg_catalog.pg_proc as p
      on p.oid = t.tgfoid and p.proname = 'lock_monetary_owner'
    join pg_catalog.pg_namespace as pn
      on pn.oid = p.pronamespace and pn.nspname = 'public';

    if configured_triggers <> 3 then
        raise exception 'Only % of 3 monetary-owner lock triggers are enabled',
            configured_triggers;
    end if;

    if has_function_privilege('anon', 'public.lock_monetary_owner()', 'EXECUTE')
        or has_function_privilege('authenticated', 'public.lock_monetary_owner()', 'EXECUTE')
        or has_function_privilege('anon', 'public.prevent_currency_relabeling()', 'EXECUTE')
        or has_function_privilege('authenticated', 'public.prevent_currency_relabeling()', 'EXECUTE')
    then
        raise exception 'A trigger-only currency serialization function is API-callable';
    end if;
end;
$$;

-- Contract-phase ACLs: every sensitive create operation goes through its checked
-- RPC while ledgers, challenge hashes and server-owned identity writes stay private.
do $$
begin
    if has_table_privilege('authenticated', 'public.expenses', 'INSERT')
        or has_table_privilege('authenticated', 'public.expenses', 'UPDATE')
        or not has_column_privilege('authenticated', 'public.expenses', 'amount', 'UPDATE')
        or has_column_privilege('authenticated', 'public.expenses', 'client_request_id', 'UPDATE')
        or has_column_privilege('authenticated', 'public.expenses', 'user_id', 'UPDATE')
        or has_table_privilege('authenticated', 'public.budget_extras', 'INSERT')
        or has_table_privilege('authenticated', 'public.budget_extras', 'UPDATE')
        or has_table_privilege('authenticated', 'public.badges', 'INSERT')
        or has_table_privilege('authenticated', 'public.goals', 'INSERT')
        or has_table_privilege('authenticated', 'public.goals', 'UPDATE')
        or has_table_privilege('authenticated', 'public.goals', 'DELETE')
        or has_table_privilege('authenticated', 'public.users', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'current_amount', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'status', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'completed_at', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'user_id', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'name', 'INSERT')
        or has_column_privilege('authenticated', 'public.goals', 'target_amount', 'INSERT')
        or has_column_privilege('authenticated', 'public.users', 'auth_id', 'INSERT')
        or has_column_privilege('authenticated', 'public.users', 'email', 'INSERT')
        or has_column_privilege('authenticated', 'public.users', 'display_name', 'INSERT')
    then
        raise exception 'Direct write grants do not match the RPC-only contract';
    end if;

    if not has_table_privilege('authenticated', 'public.goal_deposits', 'SELECT')
        or has_table_privilege('authenticated', 'public.goal_deposits', 'INSERT')
        or has_table_privilege('authenticated', 'public.goal_deposits', 'UPDATE')
        or has_table_privilege('authenticated', 'public.goal_deposits', 'DELETE')
    then
        raise exception 'Goal-deposit ledger grants do not match the contract';
    end if;

    if has_table_privilege('authenticated', 'public.whatsapp_link_challenges', 'SELECT')
        or has_table_privilege('authenticated', 'public.whatsapp_link_challenges', 'INSERT')
        or not has_table_privilege('service_role', 'public.whatsapp_link_challenges', 'SELECT')
        or not has_table_privilege('authenticated', 'public.whatsapp_links', 'SELECT')
        or has_table_privilege('authenticated', 'public.whatsapp_links', 'INSERT')
        or has_column_privilege('authenticated', 'public.users', 'phone_number', 'UPDATE')
        or not has_column_privilege('authenticated', 'public.users', 'display_name', 'UPDATE')
    then
        raise exception 'WhatsApp/profile grants do not match the contract';
    end if;

    if not has_function_privilege(
            'authenticated', 'public.deposit_to_goal(uuid,numeric,uuid)', 'EXECUTE'
        )
        or has_function_privilege('anon', 'public.deposit_to_goal(uuid,numeric,uuid)', 'EXECUTE')
        or not has_function_privilege(
            'authenticated', 'public.create_whatsapp_link_challenge()', 'EXECUTE'
        )
        or has_function_privilege('anon', 'public.create_whatsapp_link_challenge()', 'EXECUTE')
        or not has_function_privilege(
            'authenticated', 'public.unlink_whatsapp_link()', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.ensure_user_profile()', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated',
            'public.create_expense(uuid,numeric,uuid,text,date,text,text,text)',
            'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated',
            'public.expense_page(date,date,timestamptz,timestamptz,uuid,integer)',
            'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.current_expense_streak()', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.create_goal(uuid,text,numeric,date)', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.create_budget_extra(uuid,numeric,text,date)', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.unlock_badge(text)', 'EXECUTE'
        )
        or not has_function_privilege(
            'authenticated', 'public.archive_goal(uuid)', 'EXECUTE'
        )
        or has_function_privilege('anon', 'public.ensure_user_profile()', 'EXECUTE')
        or has_function_privilege(
            'anon', 'public.create_goal(uuid,text,numeric,date)', 'EXECUTE'
        )
        or has_function_privilege(
            'anon',
            'public.expense_page(date,date,timestamptz,timestamptz,uuid,integer)',
            'EXECUTE'
        )
        or has_function_privilege('authenticated',
            'public.confirm_whatsapp_link_challenge(text,text)', 'EXECUTE')
        or not has_function_privilege('service_role',
            'public.confirm_whatsapp_link_challenge(text,text)', 'EXECUTE')
    then
        raise exception 'RPC execute grants do not match the contract';
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from fluyo_private.contract_migrations
        where version = '0008'
          and filename = '0008_write_path_contract.sql'
          and sha256 ~ '^[0-9a-f]{64}$'
    ) then
        raise exception 'Contract 0008 is not verifiably registered';
    end if;
end;
$$;

insert into auth.users (id) values
    ('00000000-0000-0000-0000-000000000001'),
    ('00000000-0000-0000-0000-000000000002');

insert into public.users (id, auth_id, email) values
    ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'one@example.test'),
    ('10000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'two@example.test');

-- Aggregate outputs must not inherit numeric(10,2) from one persisted movement.
-- Five maximum expenses and two maximum extras exercise effective budget, spent,
-- remaining and extra income beyond the per-row range without changing that range.
insert into auth.users (id)
values ('00000000-0000-0000-0000-000000000003');
insert into public.users (id, auth_id, email, monthly_budget) values (
    '10000000-0000-0000-0000-000000000003',
    '00000000-0000-0000-0000-000000000003',
    'aggregate-limit@example.test',
    99999999.99
);
insert into public.budget_extras (user_id, month, amount, note, client_request_id)
select
    '10000000-0000-0000-0000-000000000003',
    date_trunc('month', timezone('America/Lima', now()))::date,
    99999999.99,
    'Maximum valid extra',
    gen_random_uuid()
from generate_series(1, 2);
insert into public.expenses (user_id, amount, expense_date, source, client_request_id)
select
    '10000000-0000-0000-0000-000000000003',
    99999999.99,
    timezone('America/Lima', now())::date,
    'manual',
    gen_random_uuid()
from generate_series(1, 5);

do $$
declare
    budget_row record;
    widened_columns integer;
begin
    select monthly_budget, total_spent, remaining, extra_income
    into strict budget_row
    from public.current_month_budget
    where user_id = '10000000-0000-0000-0000-000000000003';

    if budget_row.monthly_budget is distinct from 299999999.97::numeric
        or budget_row.total_spent is distinct from 499999999.95::numeric
        or budget_row.remaining is distinct from (-199999999.98)::numeric
        or budget_row.extra_income is distinct from 199999999.98::numeric
    then
        raise exception
            'Aggregate money mismatch: budget %, spent %, remaining %, extras %',
            budget_row.monthly_budget,
            budget_row.total_spent,
            budget_row.remaining,
            budget_row.extra_income;
    end if;

    select count(*)
    into widened_columns
    from pg_catalog.pg_attribute as a
    where a.attrelid = 'public.current_month_budget'::regclass
      and a.attname in ('monthly_budget', 'total_spent', 'remaining', 'extra_income')
      and a.atttypid = 'numeric'::regtype
      and a.atttypmod = -1
      and not a.attisdropped;

    if widened_columns <> 4 then
        raise exception 'Only % of 4 aggregate money columns are unconstrained numeric',
            widened_columns;
    end if;
end;
$$;

-- Optional profile fields remain nullable, but malformed or oversized supplied values
-- are rejected for every newly inserted/updated row.
do $$
begin
    begin
        update public.users set email = 'not-an-email'
        where id = '10000000-0000-0000-0000-000000000001';
        raise exception 'Expected malformed email rejection';
    exception when check_violation then
        null;
    end;

    begin
        update public.users set email = repeat('a', 245) || '@example.test'
        where id = '10000000-0000-0000-0000-000000000001';
        raise exception 'Expected oversized email rejection';
    exception when check_violation then
        null;
    end;

    begin
        update public.users set display_name = ' '
        where id = '10000000-0000-0000-0000-000000000001';
        raise exception 'Expected blank display-name rejection';
    exception when check_violation then
        null;
    end;

    begin
        update public.users set display_name = repeat('x', 101)
        where id = '10000000-0000-0000-0000-000000000001';
        raise exception 'Expected oversized display-name rejection';
    exception when check_violation then
        null;
    end;

    begin
        update public.users set phone_number = '51 999-123'
        where id = '10000000-0000-0000-0000-000000000001';
        raise exception 'Expected malformed legacy-phone rejection';
    exception when check_violation then
        null;
    end;
end;
$$;

-- Seed user B through the same authenticated API surface used by the app.
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000002';
set role authenticated;
insert into public.categories (id, user_id, name, icon, color, display_order)
values (
    '40000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000002',
    'User B category', 'tag', '#123ABC', 20
);
select set_config('test.user_b_expense_id', e.id::text, false)
from public.create_expense(
    '50000000-0000-0000-0000-000000000092',
    7.50,
    '40000000-0000-0000-0000-000000000002',
    null,
    timezone('America/Lima', now())::date,
    'manual',
    null,
    null
) as e;
select set_config('test.user_b_goal_id', g.id::text, false)
from public.create_goal(
    '60000000-0000-0000-0000-000000000092',
    'User B goal',
    100,
    null
) as g;
select * from public.deposit_to_goal(
    current_setting('test.user_b_goal_id')::uuid, 10,
    '30000000-0000-0000-0000-000000000098'
);
select * from public.unlock_badge('first_expense');
select set_config('test.user_b_extra_id', x.id::text, false)
from public.create_budget_extra(
    '70000000-0000-0000-0000-000000000092',
    12,
    'User B extra',
    date_trunc('month', timezone('America/Lima', now()))::date
) as x;

-- Stable request ids replay the original rows rather than duplicating money/state.
do $$
declare
    replay_id uuid;
    replay_unlocked boolean;
    profile_id uuid;
begin
    select id into strict profile_id from public.ensure_user_profile();
    if profile_id <> '10000000-0000-0000-0000-000000000002'::uuid then
        raise exception 'Profile provisioning did not return the existing profile';
    end if;

    select id into strict replay_id
    from public.create_expense(
        '50000000-0000-0000-0000-000000000092', 7.50,
        '40000000-0000-0000-0000-000000000002', null,
        timezone('America/Lima', now())::date, 'manual', null, null
    );
    if replay_id <> current_setting('test.user_b_expense_id')::uuid then
        raise exception 'Expense idempotency returned a different row';
    end if;

    select id into strict replay_id
    from public.create_goal(
        '60000000-0000-0000-0000-000000000092', 'User B goal', 100, null
    );
    if replay_id <> current_setting('test.user_b_goal_id')::uuid then
        raise exception 'Goal idempotency returned a different row';
    end if;

    select id into strict replay_id
    from public.create_budget_extra(
        '70000000-0000-0000-0000-000000000092', 12, 'User B extra',
        date_trunc('month', timezone('America/Lima', now()))::date
    );
    if replay_id <> current_setting('test.user_b_extra_id')::uuid then
        raise exception 'Budget-extra idempotency returned a different row';
    end if;

    select unlocked into strict replay_unlocked
    from public.unlock_badge('first_expense');
    if replay_unlocked is distinct from false then
        raise exception 'Badge replay was not idempotently false';
    end if;

    if (select count(*) from public.expenses
        where client_request_id = '50000000-0000-0000-0000-000000000092') <> 1
       or (select count(*) from public.goals
           where client_request_id = '60000000-0000-0000-0000-000000000092') <> 1
       or (select count(*) from public.budget_extras
           where client_request_id = '70000000-0000-0000-0000-000000000092') <> 1
    then
        raise exception 'An idempotent create operation duplicated a row';
    end if;
end;
$$;
reset role;

set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000001';
set role authenticated;

-- New goals cannot start overdue. Existing goals may naturally become overdue later.
do $$
begin
    begin
        perform * from public.create_goal(
            '60000000-0000-0000-0000-000000000099',
            'Already overdue',
            100,
            timezone('America/Lima', now())::date - 1
        );
        raise exception 'Expected past goal deadline rejection';
    exception when invalid_parameter_value then
        null;
    end;
end;
$$;

-- RLS hides user B's profile and rows from user A, independent of client filters.
do $$
declare
    visible_count integer;
begin
    select count(*) into visible_count from public.users
    where id = '10000000-0000-0000-0000-000000000002';
    if visible_count <> 0 then
        raise exception 'RLS exposed user B profile to user A';
    end if;

    select count(*) into visible_count from public.categories
    where id = '40000000-0000-0000-0000-000000000002';
    if visible_count <> 0 then
        raise exception 'RLS exposed user B category to user A';
    end if;

    select count(*) into visible_count from public.expenses
    where id = current_setting('test.user_b_expense_id')::uuid;
    if visible_count <> 0 then
        raise exception 'RLS exposed user B expense to user A';
    end if;

    select count(*) into visible_count from public.goals
    where id = current_setting('test.user_b_goal_id')::uuid;
    if visible_count <> 0 then
        raise exception 'RLS exposed user B goal to user A';
    end if;

    select count(*) into visible_count from public.goal_deposits
    where user_id = '10000000-0000-0000-0000-000000000002';
    if visible_count <> 0 then
        raise exception 'RLS exposed user B goal deposit to user A';
    end if;

    select count(*) into visible_count from public.badges
    where user_id = '10000000-0000-0000-0000-000000000002';
    if visible_count <> 0 then
        raise exception 'RLS exposed user B badge to user A';
    end if;

    select count(*) into visible_count from public.budget_extras
    where user_id = '10000000-0000-0000-0000-000000000002';
    if visible_count <> 0 then
        raise exception 'RLS exposed user B budget extra to user A';
    end if;

    begin
        perform 1 from public.whatsapp_link_challenges;
        raise exception 'Authenticated clients unexpectedly read challenge hashes';
    exception when insufficient_privilege then
        null;
    end;
end;
$$;

-- RPC ownership and the composite FK independently protect cross-user relations.
do $$
begin
    begin
        perform public.create_expense(
            '50000000-0000-0000-0000-000000000093',
            5,
            '40000000-0000-0000-0000-000000000002',
            null,
            timezone('America/Lima', now())::date,
            'manual',
            null,
            null
        );
        raise exception 'Expected cross-user category FK rejection';
    exception when foreign_key_violation then
        null;
    end;

    begin
        perform public.deposit_to_goal(
            current_setting('test.user_b_goal_id')::uuid, 5,
            '30000000-0000-0000-0000-000000000099'
        );
        raise exception 'Expected deposit-to-foreign-goal rejection';
    exception when insufficient_privilege then
        null;
    end;
end;
$$;

-- The contract rejects legacy direct paths even when their values look otherwise valid.
do $$
begin
    begin
        insert into public.expenses (
            user_id, amount, expense_date, source, client_request_id
        ) values (
            '10000000-0000-0000-0000-000000000001', 5,
            timezone('America/Lima', now())::date, 'manual',
            '50000000-0000-0000-0000-000000000094'
        );
        raise exception 'Expected direct expense INSERT rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        update public.expenses
        set client_request_id = '50000000-0000-0000-0000-000000000097'
        where id = current_setting('test.user_b_expense_id')::uuid;
        raise exception 'Expected expense idempotency-key UPDATE rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.goals (user_id, name, target_amount, client_request_id)
        values (
            '10000000-0000-0000-0000-000000000001', 'Direct goal', 50,
            '60000000-0000-0000-0000-000000000094'
        );
        raise exception 'Expected direct goal INSERT rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        delete from public.goals
        where id = current_setting('test.user_b_goal_id')::uuid;
        raise exception 'Expected direct goal DELETE rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.budget_extras (
            user_id, month, amount, note, client_request_id
        ) values (
            '10000000-0000-0000-0000-000000000001',
            date_trunc('month', timezone('America/Lima', now()))::date,
            5, 'Direct extra', '70000000-0000-0000-0000-000000000094'
        );
        raise exception 'Expected direct budget-extra INSERT rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        update public.budget_extras
        set client_request_id = '70000000-0000-0000-0000-000000000097'
        where id = current_setting('test.user_b_extra_id')::uuid;
        raise exception 'Expected budget-extra idempotency-key UPDATE rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.badges (user_id, badge_type, name)
        values (
            '10000000-0000-0000-0000-000000000001',
            'first_expense', 'Direct badge'
        );
        raise exception 'Expected direct badge INSERT rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.users (auth_id, email)
        values ('00000000-0000-0000-0000-000000000094', 'direct@example.test');
        raise exception 'Expected direct profile INSERT rejection';
    exception when insufficient_privilege then null;
    end;

    begin
        insert into public.goal_deposits (goal_id, user_id, amount)
        values (
            current_setting('test.user_b_goal_id')::uuid,
            '10000000-0000-0000-0000-000000000001', 5
        );
        raise exception 'Expected direct goal-deposit INSERT rejection';
    exception when insufficient_privilege then null;
    end;
end;
$$;

-- New string/URL constraints reject null required values, malformed formats and limits.
do $$
begin
    begin
        insert into public.categories (user_id, name, icon, color)
        values ('10000000-0000-0000-0000-000000000001', 'Null icon', null, '#123ABC');
        raise exception 'Expected null category icon rejection';
    exception when not_null_violation then
        null;
    end;

    begin
        insert into public.categories (user_id, name, icon, color)
        values ('10000000-0000-0000-0000-000000000001', 'Bad icon', '../tag', '#123ABC');
        raise exception 'Expected malformed category icon rejection';
    exception when check_violation then
        null;
    end;

    begin
        insert into public.categories (user_id, name, icon, color)
        values ('10000000-0000-0000-0000-000000000001', 'Bad color', 'tag', 'blue');
        raise exception 'Expected malformed category color rejection';
    exception when check_violation then
        null;
    end;

    begin
        perform public.create_expense(
            '50000000-0000-0000-0000-000000000095',
            5,
            null,
            null,
            timezone('America/Lima', now())::date,
            'ocr',
            null,
            'javascript:alert(1)'
        );
        raise exception 'Expected unsafe expense image URL rejection';
    exception when check_violation then
        null;
    end;

    begin
        perform public.create_expense(
            '50000000-0000-0000-0000-000000000096',
            5,
            null,
            null,
            timezone('America/Lima', now())::date,
            'ocr',
            null,
            'https://example.test/' || repeat('x', 2049)
        );
        raise exception 'Expected oversized expense image URL rejection';
    exception when check_violation then
        null;
    end;
end;
$$;

-- A real expense satisfies the database-backed first-expense badge criterion.
select set_config('test.user_a_expense_id', e.id::text, false)
from public.create_expense(
    '50000000-0000-0000-0000-000000000001',
    10,
    null,
    null,
    timezone('America/Lima', now())::date,
    'manual',
    null,
    'content://com.qolve.fluyo/receipt/1'
) as e;

-- Deleting an owned category referenced by an expense preserves the financial
-- row and owner while rendering it as "Sin categoría". The composite ownership
-- FK must also continue rejecting a foreign category (covered above).
insert into public.categories (id, user_id, name, icon, color, display_order)
values (
    '40000000-0000-0000-0000-000000000003',
    '10000000-0000-0000-0000-000000000001',
    'Disposable category', 'tag', '#456DEF', 21
);
select set_config('test.category_delete_expense_id', e.id::text, false)
from public.create_expense(
    '50000000-0000-0000-0000-000000000098',
    3,
    '40000000-0000-0000-0000-000000000003',
    'Category deletion contract',
    timezone('America/Lima', now())::date,
    'manual',
    null,
    null
) as e;
delete from public.categories
where id = '40000000-0000-0000-0000-000000000003';

do $$
declare
    surviving_owner uuid;
    surviving_category uuid;
begin
    select user_id, category_id
    into strict surviving_owner, surviving_category
    from public.expenses
    where id = current_setting('test.category_delete_expense_id')::uuid;

    if surviving_owner <> '10000000-0000-0000-0000-000000000001'::uuid
       or surviving_category is not null
    then
        raise exception
            'Category deletion changed expense ownership/category: owner %, category %',
            surviving_owner, surviving_category;
    end if;
end;
$$;

do $$
declare
    replay_id uuid;
begin
    begin
        update public.expenses
        set client_request_id = '50000000-0000-0000-0000-000000000097'
        where id = current_setting('test.user_a_expense_id')::uuid;
        raise exception 'Expected own expense idempotency-key UPDATE rejection';
    exception when insufficient_privilege then
        null;
    end;

    select id into strict replay_id
    from public.create_expense(
        '50000000-0000-0000-0000-000000000001', 10, null, null,
        timezone('America/Lima', now())::date, 'manual', null,
        'content://com.qolve.fluyo/receipt/1'
    );
    if replay_id <> current_setting('test.user_a_expense_id')::uuid
       or (select count(*) from public.expenses
           where client_request_id = '50000000-0000-0000-0000-000000000001') <> 1
    then
        raise exception 'Denied key mutation did not preserve expense idempotency';
    end if;
end;
$$;

-- The narrow edit grant retains the product's explicit expense-edit operation.
update public.expenses
set amount = 11,
    description = 'Edited safely'
where id = current_setting('test.user_a_expense_id')::uuid;

-- Keyset pages share one high-water mark: a create after page one must not shift
-- page two or introduce duplicates.
select set_config('test.page_expected_1', e.id::text, false)
from public.create_expense(
    '51000000-0000-0000-0000-000000000001', 1, null, 'Page one',
    date '2001-02-03', 'manual', null, null
) as e;
select set_config('test.page_expected_2', e.id::text, false)
from public.create_expense(
    '51000000-0000-0000-0000-000000000002', 2, null, 'Page two',
    date '2001-02-03', 'manual', null, null
) as e;
select set_config('test.page_expected_3', e.id::text, false)
from public.create_expense(
    '51000000-0000-0000-0000-000000000003', 3, null, 'Page three',
    date '2001-02-03', 'manual', null, null
) as e;

create temporary table expense_page_one as
select *
from public.expense_page(
    date '2001-02-03', date '2001-02-03', null, null, null, 2
);
select set_config('test.page_snapshot', snapshot_at::text, false)
from expense_page_one limit 1;
select set_config('test.page_cursor_created_at', created_at::text, false)
from expense_page_one order by created_at, id limit 1;
select set_config('test.page_cursor_id', id::text, false)
from expense_page_one order by created_at, id limit 1;

select set_config('test.page_post_snapshot_id', e.id::text, false)
from public.create_expense(
    '51000000-0000-0000-0000-000000000004', 4, null, 'After snapshot',
    date '2001-02-03', 'manual', null, null
) as e;

create temporary table expense_page_two as
select *
from public.expense_page(
    date '2001-02-03',
    date '2001-02-03',
    current_setting('test.page_snapshot')::timestamptz,
    current_setting('test.page_cursor_created_at')::timestamptz,
    current_setting('test.page_cursor_id')::uuid,
    2
);

do $$
declare
    received_rows integer;
    distinct_rows integer;
    expected_rows integer;
    post_snapshot_rows integer;
    current_streak integer;
begin
    with received as (
        select id from expense_page_one
        union all
        select id from expense_page_two
    )
    select count(*),
           count(distinct id),
           count(*) filter (where id in (
               current_setting('test.page_expected_1')::uuid,
               current_setting('test.page_expected_2')::uuid,
               current_setting('test.page_expected_3')::uuid
           )),
           count(*) filter (
               where id = current_setting('test.page_post_snapshot_id')::uuid
           )
    into received_rows, distinct_rows, expected_rows, post_snapshot_rows
    from received;

    if (select count(*) from expense_page_one) <> 2
       or (select count(*) from expense_page_two) <> 1
       or received_rows <> 3
       or distinct_rows <> 3
       or expected_rows <> 3
       or post_snapshot_rows <> 0
       or exists (
           select 1 from expense_page_one
           where snapshot_at <> current_setting('test.page_snapshot')::timestamptz
       )
       or exists (
           select 1 from expense_page_two
           where snapshot_at <> current_setting('test.page_snapshot')::timestamptz
       )
       or not exists (
           select 1 from public.expenses
           where id = current_setting('test.page_post_snapshot_id')::uuid
             and created_at > current_setting('test.page_snapshot')::timestamptz
       )
    then
        raise exception
            'Expense snapshot pagination failed: received %, distinct %, expected %, late %',
            received_rows, distinct_rows, expected_rows, post_snapshot_rows;
    end if;

    select streak into strict current_streak
    from public.current_expense_streak();
    if current_streak < 1 then
        raise exception 'Current expense streak ignored today''s expense: %', current_streak;
    end if;
end;
$$;

-- Constraint checks run as the migration owner because API callers cannot bypass
-- unlock_badge() after contract 0008.
reset role;
do $$
begin
    begin
        insert into public.badges (user_id, badge_type, name)
        values ('10000000-0000-0000-0000-000000000001', 'first_expense', null);
        raise exception 'Expected null badge name rejection';
    exception when not_null_violation then
        null;
    end;

    begin
        insert into public.badges (user_id, badge_type, name)
        values (
            '10000000-0000-0000-0000-000000000001', 'first_expense', repeat('x', 101)
        );
        raise exception 'Expected oversized badge name rejection';
    exception when check_violation then
        null;
    end;

    begin
        insert into public.badges (user_id, badge_type, name, description)
        values (
            '10000000-0000-0000-0000-000000000001', 'first_expense',
            'First expense', repeat('x', 501)
        );
        raise exception 'Expected oversized badge description rejection';
    exception when check_violation then
        null;
    end;

    begin
        insert into public.badges (user_id, badge_type, name, criteria)
        values (
            '10000000-0000-0000-0000-000000000001', 'first_expense',
            'First expense', repeat('x', 501)
        );
        raise exception 'Expected oversized badge criteria rejection';
    exception when check_violation then
        null;
    end;
end;
$$;
set role authenticated;

-- Only the safe creation columns are granted. Even internally consistent forged
-- balances/completion states must be rejected by column privileges.
do $$
begin
    begin
        insert into public.goals (
            user_id, name, target_amount, current_amount, status, completed_at
        ) values (
            '10000000-0000-0000-0000-000000000001', 'Forged', 50, 50,
            'completed', now()
        );
        raise exception 'Expected forged goal INSERT to be rejected';
    exception when insufficient_privilege then
        null;
    end;
end;
$$;

select set_config('test.snapshot_goal_id', g.id::text, false)
from public.create_goal(
    '60000000-0000-0000-0000-000000000001',
    'Snapshot goal',
    50,
    null
) as g;

do $$
declare
    seeded public.goals%rowtype;
begin
    select * into seeded
    from public.goals
    where id = current_setting('test.snapshot_goal_id')::uuid;
    if seeded.current_amount <> 0
        or seeded.status <> 'active'
        or seeded.completed_at is not null
    then
        raise exception 'Safe goal defaults were not applied';
    end if;
end;
$$;

create temporary table first_deposit_snapshot as
select *
from public.deposit_to_goal(
    current_setting('test.snapshot_goal_id')::uuid, 40,
    '30000000-0000-0000-0000-000000000001'
);

-- A different request mutates the live goal and completes it.
select *
from public.deposit_to_goal(
    current_setting('test.snapshot_goal_id')::uuid, 10,
    '30000000-0000-0000-0000-000000000002'
);

create temporary table replayed_deposit_snapshot as
select *
from public.deposit_to_goal(
    current_setting('test.snapshot_goal_id')::uuid, 40,
    '30000000-0000-0000-0000-000000000001'
);

do $$
declare
    first_json jsonb;
    replay_json jsonb;
    live_goal public.goals%rowtype;
begin
    select to_jsonb(f) into first_json from first_deposit_snapshot as f;
    select to_jsonb(r) into replay_json from replayed_deposit_snapshot as r;
    if first_json is distinct from replay_json then
        raise exception 'Idempotent replay changed snapshot: first %, replay %', first_json, replay_json;
    end if;

    select * into live_goal
    from public.goals
    where id = current_setting('test.snapshot_goal_id')::uuid;
    if live_goal.current_amount <> 50 or live_goal.status <> 'completed' then
        raise exception 'Second deposit did not mutate live state as expected';
    end if;
end;
$$;

select set_config('test.archive_goal_id', g.id::text, false)
from public.create_goal(
    '60000000-0000-0000-0000-000000000002',
    'Archive goal',
    50,
    null
) as g;
select *
from public.deposit_to_goal(
    current_setting('test.archive_goal_id')::uuid,
    5,
    '30000000-0000-0000-0000-000000000004'
);

-- Archiving is a repeatable logical delete: the visible goal disappears while the
-- immutable financial row and its deposit ledger remain intact.
do $$
declare
    first_archived boolean;
    replay_archived boolean;
    first_deleted_at timestamptz;
    replay_deleted_at timestamptz;
    underlying_goals integer;
    visible_goals integer;
    deposit_rows integer;
begin
    select archived into strict first_archived
    from public.archive_goal(current_setting('test.archive_goal_id')::uuid);
    select deleted_at into strict first_deleted_at
    from public.goals
    where id = current_setting('test.archive_goal_id')::uuid;

    select archived into strict replay_archived
    from public.archive_goal(current_setting('test.archive_goal_id')::uuid);
    select deleted_at into strict replay_deleted_at
    from public.goals
    where id = current_setting('test.archive_goal_id')::uuid;

    select count(*) into underlying_goals
    from public.goals
    where id = current_setting('test.archive_goal_id')::uuid;
    select count(*) into visible_goals
    from public.goals_with_deposit_count
    where id = current_setting('test.archive_goal_id')::uuid;
    select count(*) into deposit_rows
    from public.goal_deposits
    where goal_id = current_setting('test.archive_goal_id')::uuid;

    if first_archived is distinct from true
       or replay_archived is distinct from true
       or first_deleted_at is null
       or replay_deleted_at is distinct from first_deleted_at
       or underlying_goals <> 1
       or visible_goals <> 0
       or deposit_rows <> 1
    then
        raise exception
            'Logical archive contract failed: first %, replay %, timestamp %/%, row %, view %, ledger %',
            first_archived, replay_archived, first_deleted_at, replay_deleted_at,
            underlying_goals, visible_goals, deposit_rows;
    end if;

    begin
        perform public.deposit_to_goal(
            current_setting('test.archive_goal_id')::uuid,
            1,
            '30000000-0000-0000-0000-000000000003'
        );
        raise exception 'Expected archived-goal deposit rejection';
    exception when object_not_in_prerequisite_state then
        null;
    end;
end;
$$;

reset role;

-- Global retention has one usable index for each independent deletion predicate.
-- The existing (user_id, created_at) index remains available for per-user throttling
-- and audit-window cleanup inside create_whatsapp_link_challenge().
do $$
declare
    expiry_definition text;
    terminal_definition text;
begin
    select pg_catalog.pg_get_indexdef(i.indexrelid)
    into expiry_definition
    from pg_catalog.pg_index as i
    where i.indexrelid =
        'public.whatsapp_link_challenges_expiry'::pg_catalog.regclass
      and i.indisvalid
      and i.indpred is not null
      and position(
          'consumed_at IS NULL'
          in pg_catalog.pg_get_expr(i.indpred, i.indrelid)
      ) > 0
      and position(
          'invalidated_at IS NULL'
          in pg_catalog.pg_get_expr(i.indpred, i.indrelid)
      ) > 0;

    select pg_catalog.pg_get_indexdef(i.indexrelid)
    into terminal_definition
    from pg_catalog.pg_index as i
    where i.indexrelid =
        'public.whatsapp_link_challenges_terminal_created'::pg_catalog.regclass
      and i.indisvalid
      and i.indpred is not null
      and position(
          'consumed_at IS NOT NULL'
          in pg_catalog.pg_get_expr(i.indpred, i.indrelid)
      ) > 0
      and position(
          'invalidated_at IS NOT NULL'
          in pg_catalog.pg_get_expr(i.indpred, i.indrelid)
      ) > 0;

    if expiry_definition is null
       or position('(expires_at)' in expiry_definition) = 0
       or terminal_definition is null
       or position('(created_at)' in terminal_definition) = 0
       or not exists (
           select 1
           from pg_catalog.pg_index as i
           where i.indexrelid =
               'public.whatsapp_link_challenges_user_created'::pg_catalog.regclass
             and i.indisvalid
       )
    then
        raise exception
            'WhatsApp challenge retention/rate-limit indexes are incomplete';
    end if;
end;
$$;

-- Seed terminal challenges to exercise the bounded 24-hour audit window.
insert into public.whatsapp_link_challenges (
    user_id, token_hash, expires_at, consumed_at, created_at
) values (
    -- A later insert for user A must globally purge this user B row as well.
    '10000000-0000-0000-0000-000000000002',
    extensions.digest('old-consumed', 'sha256'),
    now() - interval '25 hours 50 minutes',
    now() - interval '25 hours 55 minutes',
    now() - interval '26 hours'
);
insert into public.whatsapp_link_challenges (
    user_id, token_hash, expires_at, invalidated_at, created_at
) values (
    '10000000-0000-0000-0000-000000000001',
    extensions.digest('old-invalidated', 'sha256'),
    now() - interval '25 hours 50 minutes',
    now() - interval '25 hours 55 minutes',
    now() - interval '26 hours'
);
insert into public.whatsapp_link_challenges (
    user_id, token_hash, expires_at, consumed_at, created_at
) values (
    '10000000-0000-0000-0000-000000000001',
    extensions.digest('recent-consumed', 'sha256'),
    now() - interval '1 hour 50 minutes',
    now() - interval '1 hour 55 minutes',
    now() - interval '2 hours'
);

set role authenticated;
select set_config('test.first_challenge_token', c.token, false)
from public.create_whatsapp_link_challenge() as c;

do $$
begin
    begin
        perform public.create_whatsapp_link_challenge();
        raise exception 'Expected challenge throttle rejection';
    exception when object_not_in_prerequisite_state then
        null;
    end;
end;
$$;

reset role;
do $$
declare
    total_rows integer;
    old_rows integer;
    recent_rows integer;
begin
    select count(*) into total_rows
    from public.whatsapp_link_challenges
    where user_id = '10000000-0000-0000-0000-000000000001';
    select count(*) into old_rows
    from public.whatsapp_link_challenges
    where token_hash in (
        extensions.digest('old-consumed', 'sha256'),
        extensions.digest('old-invalidated', 'sha256')
    );
    select count(*) into recent_rows
    from public.whatsapp_link_challenges
    where token_hash = extensions.digest('recent-consumed', 'sha256');

    if total_rows <> 2 or old_rows <> 0 or recent_rows <> 1 then
        raise exception 'Challenge retention failed: total %, old %, recent %',
            total_rows, old_rows, recent_rows;
    end if;
end;
$$;

-- The server-callable purge reports both deletion paths and is idempotent. An
-- INSERT trigger runs before the new row exists, so this deliberately old row
-- remains available for the first explicit call and disappears exactly once.
insert into public.whatsapp_link_challenges (
    user_id, token_hash, expires_at, invalidated_at, created_at
) values (
    '10000000-0000-0000-0000-000000000002',
    extensions.digest('explicit-global-purge', 'sha256'),
    now() - interval '25 hours 50 minutes',
    now() - interval '25 hours 55 minutes',
    now() - interval '26 hours'
);

set role service_role;
select set_config(
    'test.first_global_purge_count',
    public.purge_expired_whatsapp_link_challenges()::text,
    false
);
select set_config(
    'test.replayed_global_purge_count',
    public.purge_expired_whatsapp_link_challenges()::text,
    false
);

reset role;
do $$
begin
    if current_setting('test.first_global_purge_count')::bigint <> 1
       or current_setting('test.replayed_global_purge_count')::bigint <> 0
       or exists (
           select 1
           from public.whatsapp_link_challenges
           where token_hash = extensions.digest('explicit-global-purge', 'sha256')
       )
    then
        raise exception
            'Global purge was not counted/idempotent: first %, replay %',
            current_setting('test.first_global_purge_count'),
            current_setting('test.replayed_global_purge_count');
    end if;
end;
$$;

set role service_role;
select *
from public.confirm_whatsapp_link_challenge(
    current_setting('test.first_challenge_token'), '+51987654321'
);

-- Age the consumed request only to bypass the throttle and create a fresh,
-- outstanding challenge that unlink must invalidate.
reset role;
update public.whatsapp_link_challenges
set created_at = now() - interval '2 minutes'
where token_hash = extensions.digest(current_setting('test.first_challenge_token'), 'sha256');

set role authenticated;
select set_config('test.second_challenge_token', c.token, false)
from public.create_whatsapp_link_challenge() as c;

do $$
declare
    did_unlink boolean;
begin
    select unlinked into did_unlink from public.unlink_whatsapp_link();
    if did_unlink is distinct from true then
        raise exception 'Existing WhatsApp link was not removed';
    end if;

    select unlinked into did_unlink from public.unlink_whatsapp_link();
    if did_unlink is distinct from false then
        raise exception 'Second unlink should be idempotently false';
    end if;
end;
$$;

reset role;
set role service_role;
do $$
declare
    active_rows integer;
    linked_rows integer;
begin
    select count(*) into active_rows
    from public.whatsapp_link_challenges
    where user_id = '10000000-0000-0000-0000-000000000001'
      and consumed_at is null and invalidated_at is null;
    select count(*) into linked_rows
    from public.whatsapp_links
    where user_id = '10000000-0000-0000-0000-000000000001';

    if active_rows <> 0 or linked_rows <> 0 then
        raise exception 'Unlink left active challenges (%) or links (%)', active_rows, linked_rows;
    end if;

    begin
        perform public.confirm_whatsapp_link_challenge(
            current_setting('test.second_challenge_token'), '+51987654321'
        );
        raise exception 'Expected invalidated challenge confirmation rejection';
    exception when invalid_parameter_value then
        null;
    end;
end;
$$;

rollback;
select 'expanded PostgreSQL behavior tests passed' as result;
