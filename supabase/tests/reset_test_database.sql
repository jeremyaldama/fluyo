\set ON_ERROR_STOP on

-- DESTRUCTIVE by design. scripts/test-migrations.sh calls this only after proving that
-- the target contained no user objects at startup and after the operator supplied
-- MIGRATION_TEST_CONFIRM_RESET with the exact database name. Direct execution without
-- the per-run marker variables fails before the first DROP.
select set_config('fluyo_harness.expected_database', :'harness_database', false);
select set_config('fluyo_harness.expected_identity', :'harness_target_identity', false);
select set_config('fluyo_harness.expected_nonce', :'harness_nonce', false);

do $verify_harness_marker$
declare
    actual_identity text := current_database() || '@'
        || coalesce(inet_server_addr()::text, 'local-socket') || ':'
        || coalesce(inet_server_port()::text, 'default') || '/'
        || current_user;
begin
    if current_database() <> current_setting('fluyo_harness.expected_database')
       or actual_identity <> current_setting('fluyo_harness.expected_identity')
       or to_regclass('fluyo_test_harness.run_marker') is null
       or not exists (
           select 1
           from fluyo_test_harness.run_marker
           where database_name = current_database()
             and target_identity = actual_identity
             and run_nonce = current_setting('fluyo_harness.expected_nonce')
       )
    then
        raise exception 'Refusing reset: disposable-database marker/identity mismatch';
    end if;
end;
$verify_harness_marker$;

drop extension if exists pgcrypto cascade;
drop schema if exists fluyo_private cascade;
drop schema if exists extensions cascade;
drop schema if exists storage cascade;
drop schema if exists auth cascade;
drop schema if exists public cascade;
drop schema fluyo_test_harness cascade;

create schema public;
grant all on schema public to current_user;
grant usage on schema public to public;
