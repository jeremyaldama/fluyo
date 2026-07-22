\set ON_ERROR_STOP on

do $upgrade_post_repair$
declare
    pending_constraints integer;
    repaired_expense public.expenses%rowtype;
    repaired_goal public.goals%rowtype;
    repaired_deposit public.goal_deposits%rowtype;
    registered_contract text;
begin
    select count(*)
    into pending_constraints
    from pg_catalog.pg_constraint as c
    join pg_catalog.pg_class as t on t.oid = c.conrelid
    join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
    where n.nspname = 'public' and not c.convalidated;
    if pending_constraints <> 0 then
        raise exception 'Upgrade retained % unvalidated public constraints', pending_constraints;
    end if;

    select * into strict repaired_expense
    from public.expenses
    where id = '50000000-0000-0000-0000-000000000090';
    if repaired_expense.amount <> 5
       or repaired_expense.expense_date <> date '2000-01-01'
       or repaired_expense.image_url is not null
    then
        raise exception 'Legacy expense repair was not preserved';
    end if;

    select * into strict repaired_goal
    from public.goals
    where id = '60000000-0000-0000-0000-000000000090';
    select * into strict repaired_deposit
    from public.goal_deposits
    where id = '70000000-0000-0000-0000-000000000090';
    if repaired_goal.current_amount <> 10
       or repaired_goal.status <> 'active'
       or repaired_deposit.balance_after <> 10
       or repaired_deposit.deposit_count_after <> 1
    then
        raise exception 'Legacy goal/deposit repair is inconsistent';
    end if;

    select filename into registered_contract
    from fluyo_private.contract_migrations
    where version = '0008'
      and sha256 ~ '^[0-9a-f]{64}$';
    if registered_contract is distinct from '0008_write_path_contract.sql' then
        raise exception 'Contract 0008 registry is missing or incorrect: %', registered_contract;
    end if;

    if not exists (
        select 1 from public.badges
        where id = '80000000-0000-0000-0000-000000000090'
          and badge_type = 'first_expense'
    ) then
        raise exception 'Legacy badge repair was not retained';
    end if;
end;
$upgrade_post_repair$;
