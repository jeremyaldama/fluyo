-- 0008: contract phase for trusted, idempotent write paths.
--
-- IMPORTANT ROLLOUT GATE FOR AN EXISTING INSTALLATION:
--   1. Apply regular migrations through 0007 first.
--   2. Release an Android build that uses ensure/create/unlock/archive RPCs and sends
--      stable request UUIDs for every financial create operation.
--   3. Confirm legacy clients no longer write these tables directly (or enforce a
--      minimum supported app version).
--   4. Audit/repair legacy rows and validate staged constraints in a controlled window.
--   5. Only then apply this file with scripts/apply-contract-migrations.sh.
--
-- This file intentionally lives outside supabase/migrations so `supabase db push`
-- cannot silently apply a breaking contract in the same deployment as its expand
-- phase. Fresh installations apply it before distributing their first client.

alter table public.goal_deposits
    add constraint goal_deposits_rpc_state_required
        check (
            request_id is not null
            and balance_after is not null
            and completed_goal is not null
            and deposit_count_after is not null
            and deposit_count_after > 0
        ) not valid;

alter table public.expenses
    add constraint expenses_client_request_required
        check (client_request_id is not null) not valid;

alter table public.goals
    add constraint goals_client_request_required
        check (client_request_id is not null) not valid;

alter table public.budget_extras
    add constraint budget_extras_client_request_required
        check (client_request_id is not null) not valid;

-- Deposit rows remain an immutable ledger for mobile clients. Only the checked,
-- transaction-scoped SECURITY DEFINER RPC may append to it or mutate goal balance.
drop policy if exists goal_deposits_rw_own on public.goal_deposits;
drop policy if exists goal_deposits_select_own on public.goal_deposits;
create policy goal_deposits_select_own
    on public.goal_deposits
    for select
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

revoke insert, update, delete on public.goal_deposits
    from public, anon, authenticated;
grant select on public.goal_deposits to authenticated;

-- Every create operation below now has a checked, idempotent RPC in 0007. Remove both
-- table-level grants inherited from Supabase defaults and the legacy column grants from
-- 0006 so API callers cannot omit their request UUID or forge server-owned state.
revoke insert on public.expenses from public, anon, authenticated;
revoke insert on public.budget_extras from public, anon, authenticated;
revoke insert on public.badges from public, anon, authenticated;

-- Preserve the app's explicit expense-edit feature without allowing ownership,
-- idempotency keys, source evidence or audit timestamps to be rewritten.
revoke update on public.expenses from public, anon, authenticated;
grant update (amount, category_id, description, expense_date)
    on public.expenses to authenticated;

-- Extras have create/delete operations but no mutable edit contract. In particular,
-- client_request_id must remain immutable after the idempotent RPC returns.
revoke update on public.budget_extras from public, anon, authenticated;

revoke insert on public.goals from public, anon, authenticated;
revoke insert (user_id, name, target_amount, deadline)
    on public.goals from authenticated;
revoke update, delete on public.goals from public, anon, authenticated;

-- Profile creation is race-safe through ensure_user_profile(). Existing profile fields
-- remain narrowly updateable; identity, deletion and gamification fields stay server-owned.
revoke insert, update, delete on public.users from public, anon, authenticated;
revoke insert (auth_id, email, display_name)
    on public.users from authenticated;
grant update (
    display_name,
    monthly_budget,
    currency,
    notification_enabled,
    notification_hour,
    notification_types
) on public.users to authenticated;

-- Register the contract inside the same transaction as its ACL/constraint changes.
-- The dedicated schema is intentionally absent from the Data API roles' privileges.
create schema if not exists fluyo_private;
revoke all on schema fluyo_private from public, anon, authenticated, service_role;

create table if not exists fluyo_private.contract_migrations (
    version text primary key,
    filename text not null,
    sha256 text not null check (sha256 ~ '^[0-9a-f]{64}$'),
    applied_at timestamptz not null default now(),
    applied_by text not null default session_user
);
revoke all on table fluyo_private.contract_migrations
    from public, anon, authenticated, service_role;

-- psql deliberately does not interpolate variables inside a dollar-quoted DO
-- body. Carry the script-provided digest through a transaction-local setting so
-- both the write and the procedural verifier compare the exact same value.
select pg_catalog.set_config('fluyo.contract_sha256', :'contract_sha256', true);

insert into fluyo_private.contract_migrations (version, filename, sha256)
values (
    '0008',
    '0008_write_path_contract.sql',
    pg_catalog.current_setting('fluyo.contract_sha256')
)
on conflict (version) do nothing;

do $verify_contract_registry$
begin
    if not exists (
        select 1
        from fluyo_private.contract_migrations
        where version = '0008'
          and filename = '0008_write_path_contract.sql'
          and sha256 = pg_catalog.current_setting('fluyo.contract_sha256')
    ) then
        raise exception 'Contract registry conflicts with 0008_write_path_contract.sql';
    end if;
end;
$verify_contract_registry$;
