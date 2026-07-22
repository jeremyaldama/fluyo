\set ON_ERROR_STOP on

-- This marker is an accident guard, not an authorization mechanism. The harness creates
-- it only after proving that the target has no user objects. The destructive reset then
-- requires the same database/server/role identity and per-run nonce before dropping any
-- schema.
create schema fluyo_test_harness;
revoke all on schema fluyo_test_harness from public;

create table fluyo_test_harness.run_marker (
    database_name text primary key,
    target_identity text not null,
    run_nonce text not null,
    created_at timestamptz not null default clock_timestamp()
);
revoke all on table fluyo_test_harness.run_marker from public;

insert into fluyo_test_harness.run_marker (
    database_name,
    target_identity,
    run_nonce
) values (
    current_database(),
    current_database() || '@'
        || coalesce(inet_server_addr()::text, 'local-socket') || ':'
        || coalesce(inet_server_port()::text, 'default') || '/'
        || current_user,
    :'harness_nonce'
);
