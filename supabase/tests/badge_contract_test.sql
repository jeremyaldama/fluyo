\set ON_ERROR_STOP on
begin;

-- Exercise the public RPC exactly as an authenticated client does, while keeping
-- every fixture isolated from the broader behavior suite.
insert into auth.users (id)
values ('00000000-0000-0000-0000-000000000004');

insert into public.users (id, auth_id, email, monthly_budget)
values (
    '10000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000004',
    'badge-contract@example.test',
    10
);

select set_config(
    'request.jwt.claim.sub',
    '00000000-0000-0000-0000-000000000004',
    true
);

-- The table trigger remains fail-closed even for a privileged writer. The RPC,
-- in contrast, must return false for recognized candidates that are not yet met.
do $$
begin
    begin
        insert into public.badges (user_id, badge_type, name)
        values (
            '10000000-0000-0000-0000-000000000004',
            'streak_30',
            'Forged streak'
        );
        raise exception 'Expected forged badge criterion rejection';
    exception when check_violation then
        null;
    end;

    if has_function_privilege(
        'authenticated',
        'public.badge_criterion_met(uuid,text)',
        'EXECUTE'
    ) then
        raise exception 'Authenticated can execute the internal badge predicate';
    end if;
end;
$$;

set role authenticated;
do $$
declare
    candidate text;
    did_unlock boolean;
begin
    foreach candidate in array array[
        'first_expense', 'streak_7', 'streak_30', 'first_goal',
        'saver_month', 'mil_soles', 'no_yape', 'perfect_month'
    ]
    loop
        select unlocked into strict did_unlock
        from public.unlock_badge(candidate);
        if did_unlock is distinct from false then
            raise exception 'Unmet badge candidate % did not return false', candidate;
        end if;
    end loop;

    begin
        perform public.unlock_badge('arbitrary_admin_badge');
        raise exception 'Expected arbitrary badge type rejection';
    exception when invalid_parameter_value then
        null;
    end;

    if (select count(*) from public.badges) <> 0 then
        raise exception 'An unmet or arbitrary candidate created a badge';
    end if;
end;
$$;

-- Seven complete Lima days unlock the first/streak badges. One OCR entry keeps
-- the historical no_yape/"Semana manual" candidate false without aborting the
-- sequence, and a 30-day candidate is still normally false at this point.
reset role;
insert into public.expenses (
    user_id, amount, expense_date, source, client_request_id
)
select
    '10000000-0000-0000-0000-000000000004',
    1,
    pg_catalog.timezone('America/Lima', pg_catalog.now())::date - day_offset,
    case when day_offset = 3 then 'ocr' else 'manual' end,
    gen_random_uuid()
from generate_series(0, 6) as day_offset;

set role authenticated;
do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('first_expense');
    if did_unlock is distinct from true then
        raise exception 'first_expense did not unlock after a real expense';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('streak_7');
    if did_unlock is distinct from true then
        raise exception 'streak_7 did not unlock after seven complete days';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('no_yape');
    if did_unlock is distinct from false then
        raise exception 'no_yape unlocked despite an OCR entry in the window';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('streak_30');
    if did_unlock is distinct from false then
        raise exception 'streak_30 unlocked after only seven days';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('first_expense');
    if did_unlock is distinct from false then
        raise exception 'Badge replay was not idempotently false';
    end if;
end;
$$;

reset role;
update public.expenses
set source = 'manual'
where user_id = '10000000-0000-0000-0000-000000000004'
  and expense_date between
      pg_catalog.timezone('America/Lima', pg_catalog.now())::date - 6
      and pg_catalog.timezone('America/Lima', pg_catalog.now())::date;

set role authenticated;
do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('no_yape');
    if did_unlock is distinct from true then
        raise exception 'no_yape did not unlock after seven non-OCR days';
    end if;
end;
$$;

reset role;
insert into public.expenses (
    user_id, amount, expense_date, source, client_request_id
)
select
    '10000000-0000-0000-0000-000000000004',
    1,
    pg_catalog.timezone('America/Lima', pg_catalog.now())::date - day_offset,
    'manual',
    gen_random_uuid()
from generate_series(7, 29) as day_offset;

set role authenticated;
do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('streak_30');
    if did_unlock is distinct from true then
        raise exception 'streak_30 did not unlock after 30 complete days';
    end if;
end;
$$;

-- Completed goals remain eligible after logical archival, and lifetime savings
-- count archived goal balances for mil_soles.
select set_config('test.badge_goal_one', g.id::text, true)
from public.create_goal(
    '64000000-0000-0000-0000-000000000001',
    'Archived completed goal one',
    100,
    null
) as g;
select *
from public.deposit_to_goal(
    current_setting('test.badge_goal_one')::uuid,
    100,
    '34000000-0000-0000-0000-000000000001'
);
select *
from public.archive_goal(current_setting('test.badge_goal_one')::uuid);

do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('first_goal');
    if did_unlock is distinct from true then
        raise exception 'first_goal ignored a completed archived goal';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('mil_soles');
    if did_unlock is distinct from false then
        raise exception 'mil_soles unlocked below the 1,000-unit threshold';
    end if;
end;
$$;

select set_config('test.badge_goal_two', g.id::text, true)
from public.create_goal(
    '64000000-0000-0000-0000-000000000002',
    'Archived completed goal two',
    900,
    null
) as g;
select *
from public.deposit_to_goal(
    current_setting('test.badge_goal_two')::uuid,
    900,
    '34000000-0000-0000-0000-000000000002'
);
select *
from public.archive_goal(current_setting('test.badge_goal_two')::uuid);

do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('mil_soles');
    if did_unlock is distinct from true then
        raise exception 'mil_soles ignored archived goal balances';
    end if;
end;
$$;

-- Isolate the most recently closed month from the streak fixtures. An extra makes
-- the effective budget 60: the first S/ 11 expense exceeds the S/ 10 base budget,
-- proving that saver/perfect include the closed month's extras.
reset role;
delete from public.expenses
where user_id = '10000000-0000-0000-0000-000000000004'
  and expense_date >= (
      pg_catalog.date_trunc(
          'month',
          pg_catalog.timezone('America/Lima', pg_catalog.now())
      ) - interval '1 month'
  )::date
  and expense_date < pg_catalog.date_trunc(
      'month',
      pg_catalog.timezone('America/Lima', pg_catalog.now())
  )::date;

insert into public.budget_extras (
    user_id, month, amount, note, client_request_id
)
values (
    '10000000-0000-0000-0000-000000000004',
    (
        pg_catalog.date_trunc(
            'month',
            pg_catalog.timezone('America/Lima', pg_catalog.now())
        ) - interval '1 month'
    )::date,
    50,
    'Closed-month badge fixture',
    '74000000-0000-0000-0000-000000000001'
);

insert into public.expenses (
    user_id, amount, expense_date, source, client_request_id
)
values (
    '10000000-0000-0000-0000-000000000004',
    11,
    (
        pg_catalog.date_trunc(
            'month',
            pg_catalog.timezone('America/Lima', pg_catalog.now())
        ) - interval '1 month'
    )::date,
    'manual',
    '54000000-0000-0000-0000-000000000001'
);

set role authenticated;
do $$
declare
    did_unlock boolean;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('saver_month');
    if did_unlock is distinct from true then
        raise exception 'saver_month ignored a valid closed-month extra';
    end if;

    select unlocked into strict did_unlock
    from public.unlock_badge('perfect_month');
    if did_unlock is distinct from false then
        raise exception 'perfect_month unlocked without every day tracked';
    end if;
end;
$$;

reset role;
insert into public.expenses (
    user_id, amount, expense_date, source, client_request_id
)
select
    '10000000-0000-0000-0000-000000000004',
    1,
    (
        pg_catalog.date_trunc(
            'month',
            pg_catalog.timezone('America/Lima', pg_catalog.now())
        ) - interval '1 month'
    )::date + day_number - 1,
    'manual',
    gen_random_uuid()
from generate_series(
    2,
    pg_catalog.date_part(
        'day',
        pg_catalog.date_trunc(
            'month',
            pg_catalog.timezone('America/Lima', pg_catalog.now())
        ) - interval '1 day'
    )::integer
) as day_number;

set role authenticated;
do $$
declare
    did_unlock boolean;
    badge_count integer;
    points integer;
    current_level integer;
begin
    select unlocked into strict did_unlock
    from public.unlock_badge('perfect_month');
    if did_unlock is distinct from true then
        raise exception 'perfect_month did not unlock after every closed-month day';
    end if;

    select count(*) into badge_count
    from public.badges;
    select total_points, level into strict points, current_level
    from public.users;

    if badge_count <> 8 or points <> 141 or current_level <> 5 then
        raise exception
            'Badge derivation mismatch: count %, points %, level %',
            badge_count, points, current_level;
    end if;
end;
$$;

reset role;
rollback;
