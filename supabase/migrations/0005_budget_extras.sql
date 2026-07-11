-- 0005: "Ingreso extra del mes" — one-off amounts that raise ONLY the current
-- month's budget. The base users.monthly_budget stays intact for future months,
-- which solves the carry-over problem of editing the base for a windfall.
--
-- Apply via: supabase db push, or the Supabase MCP apply_migration.

-- ============================================================
-- BUDGET EXTRAS
-- ============================================================
create table if not exists public.budget_extras (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users(id) on delete cascade,
    -- Always the first day of the month the extra belongs to. Clients compute it
    -- from the DEVICE date so month attribution matches what the user sees.
    month date not null check (extract(day from month) = 1),
    amount numeric(10,2) not null check (amount > 0),
    note text,
    created_at timestamptz default now()
);

create index if not exists idx_budget_extras_user_month
    on public.budget_extras(user_id, month);

alter table public.budget_extras enable row level security;

drop policy if exists budget_extras_rw_own on public.budget_extras;
create policy budget_extras_rw_own
    on public.budget_extras
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- ============================================================
-- CURRENT MONTH BUDGET VIEW — monthly_budget becomes EFFECTIVE (base + extras)
-- ============================================================
-- Notes:
--  • Existing columns keep name/type/order; extra_income is APPENDED at the end
--    (CREATE OR REPLACE VIEW requirement).
--  • Extras are pre-aggregated in a subquery — joining rows directly would
--    multiply sum(e.amount) (cartesian with the expenses join).
--  • The month window uses the server's current_date (UTC), same as total_spent;
--    on the last day of the month ~19:00–24:00 America/Lima both roll early and
--    consistently. Badge evaluation is immune (it queries extras by device month).
--  • security_invoker is declared inline AND re-asserted: CREATE OR REPLACE
--    resets view reloptions, and 0003 requires it so RLS applies to readers.
create or replace view public.current_month_budget
with (security_invoker = on) as
select
    u.id as user_id,
    -- Cast back to the original column type: CREATE OR REPLACE VIEW cannot widen
    -- numeric(10,2) to numeric, and the addition would otherwise do exactly that.
    (u.monthly_budget + coalesce(x.extra, 0))::numeric(10,2) as monthly_budget,
    coalesce(sum(e.amount), 0) as total_spent,
    u.monthly_budget + coalesce(x.extra, 0) - coalesce(sum(e.amount), 0) as remaining,
    coalesce(x.extra, 0)::numeric(10,2) as extra_income
from public.users u
left join public.expenses e on e.user_id = u.id
    and date_trunc('month', e.expense_date::timestamp) = date_trunc('month', current_date::timestamp)
left join (
    select user_id, sum(amount) as extra
    from public.budget_extras
    where month = date_trunc('month', current_date)::date
    group by user_id
) x on x.user_id = u.id
group by u.id, u.monthly_budget, x.extra;

alter view public.current_month_budget set (security_invoker = on);
