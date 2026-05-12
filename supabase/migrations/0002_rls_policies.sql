-- Fluyo — Row Level Security policies
-- Each user can only read/write their own data. The seed trigger uses
-- SECURITY DEFINER so it bypasses these policies.

alter table public.users enable row level security;
alter table public.categories enable row level security;
alter table public.expenses enable row level security;
alter table public.goals enable row level security;
alter table public.badges enable row level security;
alter table public.goal_deposits enable row level security;

-- ============================================================
-- USERS — match by auth_id (the auth.uid())
-- ============================================================
drop policy if exists users_select_own on public.users;
create policy users_select_own
    on public.users
    for select
    using (auth.uid() = auth_id);

drop policy if exists users_insert_own on public.users;
create policy users_insert_own
    on public.users
    for insert
    with check (auth.uid() = auth_id);

drop policy if exists users_update_own on public.users;
create policy users_update_own
    on public.users
    for update
    using (auth.uid() = auth_id)
    with check (auth.uid() = auth_id);

-- ============================================================
-- Helper: resolve users.id from auth.uid()
-- Inline subquery in each policy avoids a function call.
-- ============================================================

-- CATEGORIES
drop policy if exists categories_rw_own on public.categories;
create policy categories_rw_own
    on public.categories
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- EXPENSES
drop policy if exists expenses_rw_own on public.expenses;
create policy expenses_rw_own
    on public.expenses
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- GOALS
drop policy if exists goals_rw_own on public.goals;
create policy goals_rw_own
    on public.goals
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- BADGES
drop policy if exists badges_rw_own on public.badges;
create policy badges_rw_own
    on public.badges
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );

-- GOAL DEPOSITS
drop policy if exists goal_deposits_rw_own on public.goal_deposits;
create policy goal_deposits_rw_own
    on public.goal_deposits
    for all
    using (
        user_id in (select id from public.users where auth_id = auth.uid())
    )
    with check (
        user_id in (select id from public.users where auth_id = auth.uid())
    );
