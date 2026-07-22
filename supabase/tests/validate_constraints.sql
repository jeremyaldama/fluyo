\set ON_ERROR_STOP on

-- A fresh schema has no legacy violations, so every staged NOT VALID constraint must
-- be validatable. Existing production databases need the separate audit/runbook first.
do $$
declare
    constraint_row record;
begin
    for constraint_row in
        select c.conrelid::regclass as relation_name, c.conname
        from pg_catalog.pg_constraint as c
        join pg_catalog.pg_class as t on t.oid = c.conrelid
        join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
        where n.nspname = 'public'
          and not c.convalidated
        order by c.conrelid::regclass::text, c.conname
    loop
        execute format(
            'alter table %s validate constraint %I',
            constraint_row.relation_name,
            constraint_row.conname
        );
    end loop;
end;
$$;

do $$
begin
    if exists (
        select 1
        from pg_catalog.pg_constraint as c
        join pg_catalog.pg_class as t on t.oid = c.conrelid
        join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
        where n.nspname = 'public' and not c.convalidated
    ) then
        raise exception 'Fresh schema retained unvalidated constraints';
    end if;
end;
$$;
