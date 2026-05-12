-- Fluyo — security hardening for Supabase advisors:
--   1. Views default to SECURITY DEFINER on Supabase; force security_invoker so RLS applies.
--   2. Pin search_path on PL/pgSQL trigger functions.
--   3. seed_default_categories is invoked only via trigger — revoke direct RPC access.

alter view public.monthly_category_summary set (security_invoker = on);
alter view public.current_month_budget set (security_invoker = on);

create or replace function public.touch_updated_at()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

revoke execute on function public.seed_default_categories() from public;
revoke execute on function public.seed_default_categories() from anon;
revoke execute on function public.seed_default_categories() from authenticated;
