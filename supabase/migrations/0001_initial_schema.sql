-- Fluyo — initial schema (Phase 1)
-- Apply via: supabase db push, or psql against the project's connection string.

create extension if not exists "pgcrypto";

-- ============================================================
-- USERS
-- Linked 1:1 with auth.users via auth_id.
-- ============================================================
create table if not exists public.users (
    id uuid primary key default gen_random_uuid(),
    auth_id uuid unique references auth.users(id) on delete cascade,
    email text,
    display_name text,
    phone_number text unique,
    monthly_budget numeric(10,2) default 0,
    currency text default 'PEN',
    level integer default 1,
    total_points integer default 0,
    notification_enabled boolean default true,
    notification_hour integer default 20,
    notification_types text[] default array['progress','reminder','budget','goal'],
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

-- ============================================================
-- CATEGORIES
-- ============================================================
create table if not exists public.categories (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users(id) on delete cascade,
    name text not null,
    icon text not null,
    color text not null,
    is_default boolean default false,
    display_order integer default 0,
    created_at timestamptz default now()
);

create index if not exists idx_categories_user on public.categories(user_id);

-- ============================================================
-- EXPENSES
-- ============================================================
create table if not exists public.expenses (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users(id) on delete cascade,
    amount numeric(10,2) not null,
    category_id uuid references public.categories(id),
    description text,
    expense_date date default current_date,
    source text not null check (source in ('manual','ocr','voice','whatsapp')),
    recipient text,
    image_url text,
    created_at timestamptz default now()
);

create index if not exists idx_expenses_user_date on public.expenses(user_id, expense_date desc);
create index if not exists idx_expenses_category on public.expenses(category_id);
-- Note: a date_trunc('month', expense_date) index would require an IMMUTABLE
-- wrapper because date_trunc(text, date) is STABLE. The user_date index covers
-- month-range queries via expense_date >= ... AND expense_date < ... ; revisit
-- if/when the EXPLAIN shows we need a dedicated month index.

-- ============================================================
-- GOALS
-- ============================================================
create table if not exists public.goals (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users(id) on delete cascade,
    name text not null,
    target_amount numeric(10,2) not null,
    current_amount numeric(10,2) default 0,
    deadline date,
    status text default 'active' check (status in ('active','completed')),
    created_at timestamptz default now(),
    completed_at timestamptz
);

create index if not exists idx_goals_user on public.goals(user_id);

-- ============================================================
-- BADGES
-- ============================================================
create table if not exists public.badges (
    id uuid primary key default gen_random_uuid(),
    user_id uuid references public.users(id) on delete cascade,
    badge_type text not null,
    name text not null,
    description text,
    criteria text,
    unlocked_at timestamptz default now(),
    unique(user_id, badge_type)
);

create index if not exists idx_badges_user on public.badges(user_id);

-- ============================================================
-- GOAL DEPOSITS
-- ============================================================
create table if not exists public.goal_deposits (
    id uuid primary key default gen_random_uuid(),
    goal_id uuid references public.goals(id) on delete cascade,
    user_id uuid references public.users(id) on delete cascade,
    amount numeric(10,2) not null,
    created_at timestamptz default now()
);

create index if not exists idx_goal_deposits_goal on public.goal_deposits(goal_id);
create index if not exists idx_goal_deposits_user on public.goal_deposits(user_id);

-- ============================================================
-- VIEWS
-- ============================================================
create or replace view public.monthly_category_summary as
select
    e.user_id,
    date_trunc('month', e.expense_date::timestamp) as month,
    e.category_id,
    c.name as category_name,
    c.color as category_color,
    sum(e.amount) as total,
    count(*) as transaction_count
from public.expenses e
join public.categories c on c.id = e.category_id
group by e.user_id, date_trunc('month', e.expense_date::timestamp), e.category_id, c.name, c.color;

create or replace view public.current_month_budget as
select
    u.id as user_id,
    u.monthly_budget,
    coalesce(sum(e.amount), 0) as total_spent,
    u.monthly_budget - coalesce(sum(e.amount), 0) as remaining
from public.users u
left join public.expenses e on e.user_id = u.id
    and date_trunc('month', e.expense_date::timestamp) = date_trunc('month', current_date::timestamp)
group by u.id, u.monthly_budget;

-- ============================================================
-- DEFAULT CATEGORY SEED TRIGGER
-- SECURITY DEFINER so it runs as the owner regardless of RLS.
-- ============================================================
create or replace function public.seed_default_categories()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.categories (user_id, name, icon, color, is_default, display_order) values
        (new.id, 'Comida', 'utensils', '#FF7043', true, 1),
        (new.id, 'Transporte', 'bus', '#42A5F5', true, 2),
        (new.id, 'Entretenimiento', 'gamepad', '#AB47BC', true, 3),
        (new.id, 'Snacks', 'coffee', '#FFA726', true, 4),
        (new.id, 'Salud', 'heart', '#EF5350', true, 5),
        (new.id, 'Educación', 'book', '#26A69A', true, 6),
        (new.id, 'Otros', 'tag', '#78909C', true, 7);
    return new;
end;
$$;

drop trigger if exists on_user_created on public.users;
create trigger on_user_created
    after insert on public.users
    for each row execute function public.seed_default_categories();

-- ============================================================
-- updated_at touch
-- ============================================================
create or replace function public.touch_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists users_touch_updated_at on public.users;
create trigger users_touch_updated_at
    before update on public.users
    for each row execute function public.touch_updated_at();
