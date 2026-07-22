create schema if not exists auth;
create table if not exists auth.users (
    id uuid primary key,
    email text,
    raw_user_meta_data jsonb default '{}'::jsonb
);

-- Keep the local stand-in upgrade-safe when a pre-existing fixture was created by an
-- older harness. Hosted Supabase already exposes both columns on auth.users.
alter table auth.users
    add column if not exists email text,
    add column if not exists raw_user_meta_data jsonb default '{}'::jsonb;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin bypassrls;
    end if;
end;
$$;

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;

grant usage on schema auth to anon, authenticated, service_role;
grant execute on function auth.uid() to anon, authenticated, service_role;

-- Minimal Supabase Storage stand-in. It makes the versioned bucket and policy block in
-- 0007 executable in vanilla PostgreSQL CI instead of silently skipping that contract.
create schema if not exists storage;
create table if not exists storage.buckets (
    id text primary key,
    name text not null unique,
    public boolean not null default false,
    file_size_limit bigint,
    allowed_mime_types text[]
);
create table if not exists storage.objects (
    id uuid primary key default gen_random_uuid(),
    bucket_id text not null references storage.buckets(id) on delete cascade,
    name text not null,
    unique (bucket_id, name)
);
create or replace function storage.foldername(name text)
returns text[]
language sql
immutable
strict
as $$
    select case
        when position('/' in name) = 0 then array[]::text[]
        else regexp_split_to_array(regexp_replace(name, '/[^/]*$', ''), '/')
    end;
$$;

grant usage on schema storage to anon, authenticated, service_role;
grant execute on function storage.foldername(text) to anon, authenticated, service_role;

-- Simulate a legacy database where API roles inherited CREATE on public. Migration
-- 0006 must revoke it instead of relying on PostgreSQL 15+'s safer fresh defaults.
grant create on schema public to public, anon, authenticated, service_role;

alter default privileges in schema public
    grant select, insert, update, delete on tables to authenticated;
alter default privileges in schema public
    grant all on tables to service_role;
alter default privileges in schema public
    grant execute on functions to anon, authenticated, service_role;
