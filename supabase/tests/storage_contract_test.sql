\set ON_ERROR_STOP on

begin;

do $storage_metadata$
declare
    configured record;
begin
    select public, file_size_limit, allowed_mime_types
    into strict configured
    from storage.buckets
    where id = 'receipts';

    if configured.public
       or configured.file_size_limit <> 10485760
       or configured.allowed_mime_types is distinct from
          array['image/jpeg', 'image/png', 'image/webp']::text[]
    then
        raise exception 'Receipts bucket safety contract is incomplete';
    end if;

    if not exists (
        select 1
        from pg_catalog.pg_policies
        where schemaname = 'storage'
          and tablename = 'objects'
          and policyname = 'fluyo_receipts_insert_active_own'
    ) then
        raise exception 'Active-owner Storage insert policy is missing';
    end if;
end;
$storage_metadata$;

insert into auth.users (id, email)
values
    ('00000000-0000-0000-0000-000000000091', 'storage-owner@example.test'),
    ('00000000-0000-0000-0000-000000000092', 'storage-other@example.test');
insert into public.users (id, auth_id, email)
values
    (
        '10000000-0000-0000-0000-000000000091',
        '00000000-0000-0000-0000-000000000091',
        'storage-owner@example.test'
    ),
    (
        '10000000-0000-0000-0000-000000000092',
        '00000000-0000-0000-0000-000000000092',
        'storage-other@example.test'
    );

set local request.jwt.claim.sub = '00000000-0000-0000-0000-000000000091';
set local role authenticated;
insert into storage.objects (bucket_id, name)
values ('receipts', '00000000-0000-0000-0000-000000000091/before-delete.png');

do $owner_storage_access$
declare
    visible_rows integer;
    changed_rows integer;
begin
    select count(*) into visible_rows
    from storage.objects
    where bucket_id = 'receipts';
    if visible_rows <> 1 then
        raise exception 'Storage owner cannot read their own object';
    end if;

    update storage.objects
    set name = '00000000-0000-0000-0000-000000000091/renamed.png'
    where bucket_id = 'receipts'
      and name = '00000000-0000-0000-0000-000000000091/before-delete.png';
    get diagnostics changed_rows = row_count;
    if changed_rows <> 1 then
        raise exception 'Storage owner cannot update inside their own prefix';
    end if;
end;
$owner_storage_access$;

reset role;
set local request.jwt.claim.sub = '00000000-0000-0000-0000-000000000092';
set local role authenticated;
do $cross_user_storage_isolation$
declare
    visible_rows integer;
    changed_rows integer;
begin
    select count(*) into visible_rows
    from storage.objects
    where bucket_id = 'receipts';
    if visible_rows <> 0 then
        raise exception 'Storage exposed another user''s object';
    end if;

    begin
        insert into storage.objects (bucket_id, name)
        values ('receipts', '00000000-0000-0000-0000-000000000091/cross-user.png');
        raise exception 'Storage allowed an insert into another user''s prefix';
    exception when insufficient_privilege then
        null;
    end;

    update storage.objects
    set name = '00000000-0000-0000-0000-000000000092/stolen.png'
    where bucket_id = 'receipts'
      and name = '00000000-0000-0000-0000-000000000091/renamed.png';
    get diagnostics changed_rows = row_count;
    if changed_rows <> 0 then
        raise exception 'Storage allowed a cross-user update';
    end if;

    delete from storage.objects
    where bucket_id = 'receipts'
      and name = '00000000-0000-0000-0000-000000000091/renamed.png';
    get diagnostics changed_rows = row_count;
    if changed_rows <> 0 then
        raise exception 'Storage allowed a cross-user delete';
    end if;
end;
$cross_user_storage_isolation$;

reset role;
set local request.jwt.claim.sub = '00000000-0000-0000-0000-000000000091';
set local role service_role;
select * from public.begin_account_deletion('00000000-0000-0000-0000-000000000091');

reset role;
set local role authenticated;
do $blocked_after_tombstone$
declare
    visible_rows integer;
    deleted_rows integer;
begin
    begin
        insert into storage.objects (bucket_id, name)
        values ('receipts', '00000000-0000-0000-0000-000000000091/after-delete.png');
        raise exception 'Storage accepted a write after the account tombstone';
    exception when insufficient_privilege then
        null;
    end;

    begin
        update storage.objects
        set name = '00000000-0000-0000-0000-000000000091/blocked-rename.png'
        where bucket_id = 'receipts'
          and name = '00000000-0000-0000-0000-000000000091/renamed.png';
        raise exception 'Storage accepted an update after the account tombstone';
    exception when insufficient_privilege then
        null;
    end;

    select count(*) into visible_rows
    from storage.objects
    where bucket_id = 'receipts'
      and name = '00000000-0000-0000-0000-000000000091/renamed.png';
    if visible_rows <> 1 then
        raise exception 'Storage tombstone unexpectedly hid existing cleanup data';
    end if;

    delete from storage.objects
    where bucket_id = 'receipts'
      and name = '00000000-0000-0000-0000-000000000091/renamed.png';
    get diagnostics deleted_rows = row_count;
    if deleted_rows <> 1 then
        raise exception 'Storage cleanup delete did not remove the owner object';
    end if;
end;
$blocked_after_tombstone$;

rollback;
select 'versioned Storage tombstone contract passed' as result;
