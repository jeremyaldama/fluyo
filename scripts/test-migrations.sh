#!/usr/bin/env bash
# Exercises both supported database histories against one explicitly disposable target:
#   1. fresh:   bootstrap -> every regular migration -> every contract -> behavior tests
#   2. upgrade: 0001..0005 -> legacy fixture -> remaining migrations -> audited repair
#               -> validation -> every contract -> behavior tests
#
# Required:
#   DATABASE_URL=postgresql://...              an empty, disposable database
#   MIGRATION_TEST_CONFIRM_RESET=<db-name>     exact current_database() value
set -Eeuo pipefail

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL must point to an empty, disposable PostgreSQL database." >&2
  exit 2
fi

if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required to run migration tests." >&2
  exit 2
fi

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly MIGRATIONS_DIR="$PROJECT_ROOT/supabase/migrations"
readonly CONTRACTS_DIR="$PROJECT_ROOT/supabase/contract-migrations"
readonly BOOTSTRAP="$PROJECT_ROOT/supabase/tests/bootstrap.sql"
readonly DATABASE_MARKER="$PROJECT_ROOT/supabase/tests/mark_test_database.sql"
readonly RESET_DATABASE="$PROJECT_ROOT/supabase/tests/reset_test_database.sql"
readonly LEGACY_FIXTURE="$PROJECT_ROOT/supabase/tests/upgrade_legacy_fixture.sql"
readonly PRE_REPAIR_TEST="$PROJECT_ROOT/supabase/tests/upgrade_pre_repair_test.sql"
readonly LEGACY_REPAIR="$PROJECT_ROOT/supabase/tests/upgrade_repair.sql"
readonly POST_UPGRADE_TEST="$PROJECT_ROOT/supabase/tests/upgrade_post_repair_test.sql"
readonly STORAGE_CONTRACT_TEST="$PROJECT_ROOT/supabase/tests/storage_contract_test.sql"
readonly BADGE_CONTRACT_TEST="$PROJECT_ROOT/supabase/tests/badge_contract_test.sql"
readonly BEHAVIOR_TEST="$PROJECT_ROOT/supabase/tests/behavior_test.sql"
readonly CURRENCY_RACE_TEST="$PROJECT_ROOT/supabase/tests/currency_race_test.sh"
readonly VALIDATE_CONSTRAINTS="$PROJECT_ROOT/supabase/tests/validate_constraints.sql"
readonly APPLY_CONTRACTS="$PROJECT_ROOT/scripts/apply-contract-migrations.sh"
readonly -a PSQL=(psql "$DATABASE_URL" --no-psqlrc --set ON_ERROR_STOP=1)

export LC_ALL=C
shopt -s nullglob
MIGRATIONS=("$MIGRATIONS_DIR"/*.sql)
CONTRACTS=("$CONTRACTS_DIR"/*.sql)
shopt -u nullglob

sql_version() {
  local filename="${1##*/}"
  if [[ ! "$filename" =~ ^([0-9]{4})_[a-z0-9_]+[.]sql$ ]]; then
    echo "SQL filename must be <4-digit-version>_<lowercase-name>.sql: $filename" >&2
    return 2
  fi
  printf '%d\n' "$((10#${BASH_REMATCH[1]}))"
}

validate_ordered_files() {
  local kind="$1"
  shift
  local previous=-1
  local file version

  if [[ "$#" -eq 0 ]]; then
    echo "No $kind SQL files were discovered." >&2
    return 2
  fi

  for file in "$@"; do
    version="$(sql_version "$file")"
    if (( version <= previous )); then
      echo "$kind SQL versions must be unique and strictly increasing: ${file##*/}" >&2
      return 2
    fi
    previous="$version"
  done
}

validate_ordered_files "migration" "${MIGRATIONS[@]}"
validate_ordered_files "contract migration" "${CONTRACTS[@]}"

last_migration_version="$(sql_version "${MIGRATIONS[${#MIGRATIONS[@]} - 1]}")"
first_contract_version="$(sql_version "${CONTRACTS[0]}")"
if (( first_contract_version <= last_migration_version )); then
  echo "Contract versions must sort after regular migrations." >&2
  exit 2
fi

for expected_version in 1 2 3 4 5; do
  found=false
  for migration in "${MIGRATIONS[@]}"; do
    if (( $(sql_version "$migration") == expected_version )); then
      found=true
      break
    fi
  done
  if [[ "$found" != true ]]; then
    printf 'Required baseline migration %04d is missing.\n' "$expected_version" >&2
    exit 2
  fi
done

for required_file in \
    "$BOOTSTRAP" \
    "$DATABASE_MARKER" \
    "$RESET_DATABASE" \
    "$LEGACY_FIXTURE" \
    "$PRE_REPAIR_TEST" \
    "$LEGACY_REPAIR" \
    "$POST_UPGRADE_TEST" \
    "$STORAGE_CONTRACT_TEST" \
    "$BADGE_CONTRACT_TEST" \
    "$BEHAVIOR_TEST" \
    "$CURRENCY_RACE_TEST" \
    "$VALIDATE_CONSTRAINTS" \
    "$APPLY_CONTRACTS"; do
  if [[ ! -f "$required_file" ]]; then
    echo "Required test file is missing: $required_file" >&2
    exit 2
  fi
done

database_name="$("${PSQL[@]}" --tuples-only --no-align --command 'select current_database()')"
if [[ -z "$database_name" || "$database_name" =~ ^(postgres|template0|template1)$ ]]; then
  echo "Refusing to run destructive migration tests against database: $database_name" >&2
  exit 2
fi
if [[ "${MIGRATION_TEST_CONFIRM_RESET:-}" != "$database_name" ]]; then
  echo "Migration tests reset schemas between fresh and upgrade scenarios." >&2
  echo "Set MIGRATION_TEST_CONFIRM_RESET to the exact disposable database name: $database_name" >&2
  exit 2
fi
contract_target_identity="$(
  "${PSQL[@]}" --tuples-only --no-align --command \
    "select current_database() || '@'
            || coalesce(inet_server_addr()::text, 'local-socket') || ':'
            || coalesce(inet_server_port()::text, 'default') || '/'
            || current_user"
)"
readonly harness_nonce="run-${BASHPID}-$(date +%s)-${RANDOM}-${RANDOM}"

# A confirmation value alone is insufficient: the target must also be empty when the
# harness starts. Reject custom schemas and public relations/routines/types, not only
# base tables; the reset deliberately destroys the schemas created by this harness.
existing_application_objects="$(
  "${PSQL[@]}" --tuples-only --no-align --command \
    "select
       (select count(*)
          from pg_catalog.pg_namespace
         where nspname <> 'public'
           and nspname <> 'information_schema'
           and nspname !~ '^pg_')
       + (select count(*)
            from pg_catalog.pg_class as c
            join pg_catalog.pg_namespace as n on n.oid = c.relnamespace
           where n.nspname = 'public')
       + (select count(*)
            from pg_catalog.pg_proc as p
            join pg_catalog.pg_namespace as n on n.oid = p.pronamespace
           where n.nspname = 'public')
       + (select count(*)
            from pg_catalog.pg_type as t
            join pg_catalog.pg_namespace as n on n.oid = t.typnamespace
           where n.nspname = 'public')
       + (select count(*)
            from pg_catalog.pg_extension
           where extname <> 'plpgsql');"
)"
if [[ "$existing_application_objects" != "0" ]]; then
  echo "Refusing to test migrations: DATABASE_URL is not an empty disposable database." >&2
  exit 2
fi

apply_sql_file() {
  local label="$1"
  local file="$2"
  echo "==> $label ${file##*/}"
  "${PSQL[@]}" --single-transaction --file "$file"
}

mark_disposable_database() {
  echo "==> Marking the verified empty target for this harness run"
  "${PSQL[@]}" --set harness_nonce="$harness_nonce" \
    --single-transaction --file "$DATABASE_MARKER"
}

reset_disposable_database() {
  echo "==> Resetting test schemas with ${RESET_DATABASE##*/}"
  "${PSQL[@]}" \
    --set harness_database="$database_name" \
    --set harness_target_identity="$contract_target_identity" \
    --set harness_nonce="$harness_nonce" \
    --single-transaction --file "$RESET_DATABASE"
}

apply_contracts() {
  CONTRACT_MIGRATION_CONFIRM="APPLY_CONTRACTS:$contract_target_identity" \
    "$APPLY_CONTRACTS"
}

run_behavior_suite() {
  local scenario="$1"
  echo "==> Running versioned Storage policy tests ($scenario)"
  "${PSQL[@]}" --file "$STORAGE_CONTRACT_TEST"

  echo "==> Running authoritative badge/RPC tests ($scenario)"
  "${PSQL[@]}" --file "$BADGE_CONTRACT_TEST"

  echo "==> Running PostgreSQL behavior tests ($scenario)"
  # behavior_test.sql owns its BEGIN/ROLLBACK so its fixture data never persists.
  "${PSQL[@]}" --file "$BEHAVIOR_TEST"

  echo "==> Running two-session currency concurrency test ($scenario)"
  DATABASE_URL="$DATABASE_URL" bash "$CURRENCY_RACE_TEST"
}

echo "==> Fresh-install scenario on confirmed disposable database: $database_name"
mark_disposable_database
apply_sql_file "Preparing Supabase-compatible roles and auth.uid():" "$BOOTSTRAP"
for migration in "${MIGRATIONS[@]}"; do
  apply_sql_file "Applying migration" "$migration"
done
apply_sql_file "Validating expand-phase constraints:" "$VALIDATE_CONSTRAINTS"
apply_contracts
apply_sql_file "Validating contract-phase constraints:" "$VALIDATE_CONSTRAINTS"
run_behavior_suite "fresh"

echo "==> Resetting only the confirmed disposable database for the upgrade scenario"
reset_disposable_database
apply_sql_file "Re-preparing Supabase-compatible roles and auth.uid():" "$BOOTSTRAP"

for migration in "${MIGRATIONS[@]}"; do
  if (( $(sql_version "$migration") <= 5 )); then
    apply_sql_file "Applying legacy baseline migration" "$migration"
  fi
done
apply_sql_file "Loading representative legacy data with" "$LEGACY_FIXTURE"

for migration in "${MIGRATIONS[@]}"; do
  if (( $(sql_version "$migration") > 5 )); then
    apply_sql_file "Applying upgrade migration" "$migration"
  fi
done

apply_sql_file "Proving legacy rows survived and require repair with" "$PRE_REPAIR_TEST"
apply_sql_file "Applying explicit legacy repair fixture" "$LEGACY_REPAIR"
apply_sql_file "Validating repaired expand-phase constraints with" "$VALIDATE_CONSTRAINTS"
apply_contracts
apply_sql_file "Validating contract-phase constraints with" "$VALIDATE_CONSTRAINTS"
apply_sql_file "Checking repaired upgrade state with" "$POST_UPGRADE_TEST"
run_behavior_suite "upgrade"

echo "==> Fresh and legacy-upgrade migration scenarios passed"
