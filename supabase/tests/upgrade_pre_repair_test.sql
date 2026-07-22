\set ON_ERROR_STOP on

do $upgrade_pre_repair$
declare
    surviving_rows integer;
    pending_constraints integer;
begin
    select
        (select count(*) from public.categories where id = '40000000-0000-0000-0000-000000000090')
        + (select count(*) from public.expenses where id = '50000000-0000-0000-0000-000000000090')
        + (select count(*) from public.goals where id = '60000000-0000-0000-0000-000000000090')
        + (select count(*) from public.goal_deposits where id = '70000000-0000-0000-0000-000000000090')
        + (select count(*) from public.badges where id = '80000000-0000-0000-0000-000000000090')
        + (select count(*) from public.budget_extras where id = '90000000-0000-0000-0000-000000000090')
    into surviving_rows;

    if surviving_rows <> 6 then
        raise exception 'Upgrade deleted legacy rows: only % of 6 survived', surviving_rows;
    end if;

    select count(*)
    into pending_constraints
    from pg_catalog.pg_constraint as c
    join pg_catalog.pg_class as t on t.oid = c.conrelid
    join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
    where n.nspname = 'public' and not c.convalidated;

    if pending_constraints = 0 then
        raise exception 'Expected staged NOT VALID constraints before legacy repair';
    end if;

    begin
        execute 'alter table public.expenses validate constraint expenses_amount_valid';
        raise exception 'Invalid legacy expense unexpectedly passed validation';
    exception when check_violation then
        null;
    end;

    begin
        execute 'alter table public.goals validate constraint goals_completion_state_valid';
        raise exception 'Invalid legacy goal unexpectedly passed validation';
    exception when check_violation then
        null;
    end;

    if to_regprocedure('public.create_expense(uuid,numeric,uuid,text,date,text,text,text)') is null
       or to_regprocedure('public.ensure_user_profile()') is null
    then
        raise exception 'Remaining expand migrations were not applied before repair';
    end if;
end;
$upgrade_pre_repair$;
