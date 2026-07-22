#!/usr/bin/env bash
# Applies explicitly gated contract migrations in version order. Each contract must
# register itself in fluyo_private.contract_migrations before its transaction commits.
set -Eeuo pipefail

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL is required." >&2
  exit 2
fi
if ! command -v psql >/dev/null 2>&1; then
  echo "psql is required." >&2
  exit 2
fi
if ! command -v sha256sum >/dev/null 2>&1 && ! command -v shasum >/dev/null 2>&1; then
  echo "sha256sum (or shasum) is required to verify contract contents." >&2
  exit 2
fi

readonly SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
readonly PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/.." && pwd)"
readonly MIGRATIONS_DIR="$PROJECT_ROOT/supabase/migrations"
readonly CONTRACTS_DIR="$PROJECT_ROOT/supabase/contract-migrations"
readonly -a PSQL=(psql "$DATABASE_URL" --no-psqlrc --set ON_ERROR_STOP=1)

sha256_file() {
  local output digest
  if command -v sha256sum >/dev/null 2>&1; then
    output="$(sha256sum -- "$1")"
  else
    output="$(shasum -a 256 -- "$1")"
  fi
  digest="${output%% *}"
  if [[ ! "$digest" =~ ^[0-9a-f]{64}$ ]]; then
    echo "Could not compute a valid SHA-256 for $1." >&2
    return 1
  fi
  printf '%s\n' "$digest"
}

export LC_ALL=C
shopt -s nullglob
MIGRATIONS=("$MIGRATIONS_DIR"/*.sql)
CONTRACTS=("$CONTRACTS_DIR"/*.sql)
shopt -u nullglob
if [[ "${#MIGRATIONS[@]}" -eq 0 ]]; then
  echo "No regular migrations were found in $MIGRATIONS_DIR." >&2
  exit 2
fi
if [[ "${#CONTRACTS[@]}" -eq 0 ]]; then
  echo "No contract migrations were found in $CONTRACTS_DIR." >&2
  exit 2
fi

last_migration_version=-1
for migration in "${MIGRATIONS[@]}"; do
  migration_filename="${migration##*/}"
  if [[ ! "$migration_filename" =~ ^([0-9]{4})_[a-z0-9_]+[.]sql$ ]]; then
    echo "Invalid regular migration filename: $migration_filename" >&2
    exit 2
  fi
  migration_version="$((10#${BASH_REMATCH[1]}))"
  if (( migration_version <= last_migration_version )); then
    echo "Regular migration versions are not unique/increasing: $migration_filename" >&2
    exit 2
  fi
  last_migration_version="$migration_version"
done

database_name="$("${PSQL[@]}" --tuples-only --no-align --command 'select current_database()')"
target_identity="$(
  "${PSQL[@]}" --tuples-only --no-align --field-separator='|' --command \
    "select current_database(), coalesce(inet_server_addr()::text, 'local-socket'),
            coalesce(inet_server_port()::text, 'default'), current_user"
)"
IFS='|' read -r target_database target_address target_port target_role <<<"$target_identity"
readonly target_confirmation_identity="${target_database}@${target_address}:${target_port}/${target_role}"
readonly expected_confirmation="APPLY_CONTRACTS:$target_confirmation_identity"

echo "Contract migration target (database|address|port|role): $target_identity"
if [[ "${CONTRACT_MIGRATION_CONFIRM:-}" != "$expected_confirmation" ]]; then
  echo "Contract migrations are intentionally not automatic." >&2
  echo "After retiring legacy writers, rerun with:" >&2
  echo "  CONTRACT_MIGRATION_CONFIRM='$expected_confirmation'" >&2
  exit 2
fi

# Contract 0008 relies on all expand-phase RPCs and columns. Checking before any write
# produces a clear failure on an incomplete target.
prerequisites_ready="$(
  "${PSQL[@]}" --tuples-only --no-align --command \
    "select
       to_regprocedure('public.deposit_to_goal(uuid,numeric,uuid)') is not null
       and to_regprocedure('public.ensure_user_profile()') is not null
       and to_regprocedure(
         'public.create_expense(uuid,numeric,uuid,text,date,text,text,text)'
       ) is not null
       and to_regprocedure('public.create_goal(uuid,text,numeric,date)') is not null
       and to_regprocedure('public.create_budget_extra(uuid,numeric,text,date)') is not null
       and to_regprocedure('public.unlock_badge(text)') is not null
       and to_regprocedure('public.archive_goal(uuid)') is not null
       and exists (
           select 1 from information_schema.columns
           where table_schema = 'public' and table_name = 'goal_deposits'
             and column_name = 'request_id'
       )
       and exists (
           select 1 from information_schema.columns
           where table_schema = 'public' and table_name = 'expenses'
             and column_name = 'client_request_id'
       )
       and exists (
           select 1 from information_schema.columns
           where table_schema = 'public' and table_name = 'goals'
             and column_name = 'client_request_id'
       )
       and exists (
           select 1 from information_schema.columns
           where table_schema = 'public' and table_name = 'budget_extras'
             and column_name = 'client_request_id'
       );"
)"
if [[ "$prerequisites_ready" != "t" ]]; then
  echo "Contract prerequisites are missing; apply all regular migrations first." >&2
  exit 1
fi

legacy_rows_without_contract_state="$(
  "${PSQL[@]}" --tuples-only --no-align --command \
    "select
       (select count(*) from public.expenses where client_request_id is null)
       + (select count(*) from public.goals where client_request_id is null)
       + (select count(*) from public.budget_extras where client_request_id is null)
       + (select count(*) from public.goal_deposits
           where request_id is null
              or balance_after is null
              or completed_goal is null
              or deposit_count_after is null
              or deposit_count_after <= 0);"
)"
if [[ "$legacy_rows_without_contract_state" != "0" ]]; then
  echo "Refusing contract activation: $legacy_rows_without_contract_state historical rows" >&2
  echo "still lack reviewed request/snapshot state. Repair and audit them before retrying." >&2
  exit 1
fi

unvalidated_expand_constraints="$(
  "${PSQL[@]}" --tuples-only --no-align --command \
    "select count(*)
       from pg_catalog.pg_constraint as c
       join pg_catalog.pg_class as t on t.oid = c.conrelid
       join pg_catalog.pg_namespace as n on n.oid = t.relnamespace
      where n.nspname = 'public'
        and not c.convalidated
        and c.conname not in (
          'goal_deposits_rpc_state_required',
          'expenses_client_request_required',
          'goals_client_request_required',
          'budget_extras_client_request_required'
        );"
)"
if [[ "$unvalidated_expand_constraints" != "0" ]]; then
  echo "Refusing contract activation: $unvalidated_expand_constraints expand-phase" >&2
  echo "constraints remain unvalidated. Repair, validate and record them first." >&2
  exit 1
fi

verify_contract_effects() {
  local raw_version="$1"
  local expected_filename="$2"
  local expected_sha256="$3"
  local verified

  case "$raw_version" in
    0008)
      verified="$(
        "${PSQL[@]}" --tuples-only --no-align --command \
          "select
             exists (
               select 1 from fluyo_private.contract_migrations
                where version = '$raw_version'
                  and filename = '$expected_filename'
                  and sha256 = '$expected_sha256'
             )
             and exists (
               select 1 from pg_catalog.pg_constraint as c
                where c.conrelid = 'public.expenses'::regclass
                  and c.conname = 'expenses_client_request_required'
                  and c.contype = 'c'
                  and position(
                    'client_request_id IS NOT NULL'
                    in pg_catalog.pg_get_expr(c.conbin, c.conrelid)
                  ) > 0
             )
             and exists (
               select 1 from pg_catalog.pg_constraint as c
                where c.conrelid = 'public.goals'::regclass
                  and c.conname = 'goals_client_request_required'
                  and c.contype = 'c'
                  and position(
                    'client_request_id IS NOT NULL'
                    in pg_catalog.pg_get_expr(c.conbin, c.conrelid)
                  ) > 0
             )
             and exists (
               select 1 from pg_catalog.pg_constraint as c
                where c.conrelid = 'public.budget_extras'::regclass
                  and c.conname = 'budget_extras_client_request_required'
                  and c.contype = 'c'
                  and position(
                    'client_request_id IS NOT NULL'
                    in pg_catalog.pg_get_expr(c.conbin, c.conrelid)
                  ) > 0
             )
             and exists (
               select 1 from pg_catalog.pg_constraint as c
                where c.conrelid = 'public.goal_deposits'::regclass
                  and c.conname = 'goal_deposits_rpc_state_required'
                  and c.contype = 'c'
                  and position('request_id IS NOT NULL' in pg_catalog.pg_get_expr(c.conbin, c.conrelid)) > 0
                  and position('balance_after IS NOT NULL' in pg_catalog.pg_get_expr(c.conbin, c.conrelid)) > 0
                  and position('completed_goal IS NOT NULL' in pg_catalog.pg_get_expr(c.conbin, c.conrelid)) > 0
                  and position('deposit_count_after IS NOT NULL' in pg_catalog.pg_get_expr(c.conbin, c.conrelid)) > 0
             )
             and exists (
               select 1 from pg_catalog.pg_policies
                where schemaname = 'public'
                  and tablename = 'goal_deposits'
                  and policyname = 'goal_deposits_select_own'
                  and cmd = 'SELECT'
                  and position('user_id' in coalesce(qual, '')) > 0
                  and position('auth.uid()' in coalesce(qual, '')) > 0
             )
             and not exists (
               select 1 from pg_catalog.pg_policies
                where schemaname = 'public'
                  and tablename = 'goal_deposits'
                  and policyname = 'goal_deposits_rw_own'
             )
             and not has_table_privilege('authenticated', 'public.expenses', 'INSERT')
             and not has_table_privilege('authenticated', 'public.expenses', 'UPDATE')
             and has_column_privilege('authenticated', 'public.expenses', 'amount', 'UPDATE')
             and has_column_privilege('authenticated', 'public.expenses', 'category_id', 'UPDATE')
             and has_column_privilege('authenticated', 'public.expenses', 'description', 'UPDATE')
             and has_column_privilege('authenticated', 'public.expenses', 'expense_date', 'UPDATE')
             and not has_column_privilege('authenticated', 'public.expenses', 'client_request_id', 'UPDATE')
             and not has_column_privilege('authenticated', 'public.expenses', 'user_id', 'UPDATE')
             and not has_table_privilege('authenticated', 'public.budget_extras', 'INSERT')
             and not has_table_privilege('authenticated', 'public.budget_extras', 'UPDATE')
             and not has_table_privilege('authenticated', 'public.badges', 'INSERT')
             and not has_table_privilege('authenticated', 'public.goals', 'INSERT')
             and not has_table_privilege('authenticated', 'public.goals', 'UPDATE')
             and not has_table_privilege('authenticated', 'public.goals', 'DELETE')
             and not has_table_privilege('authenticated', 'public.users', 'INSERT')
             and not has_table_privilege('authenticated', 'public.users', 'UPDATE')
             and has_column_privilege('authenticated', 'public.users', 'display_name', 'UPDATE')
             and not has_column_privilege('authenticated', 'public.users', 'phone_number', 'UPDATE')
             and has_table_privilege('authenticated', 'public.goal_deposits', 'SELECT')
             and not has_table_privilege('authenticated', 'public.goal_deposits', 'INSERT')
             and not has_table_privilege('authenticated', 'public.goal_deposits', 'UPDATE')
             and not has_table_privilege('authenticated', 'public.goal_deposits', 'DELETE');"
      )"
      ;;
    *)
      echo "No postcondition verifier is defined for contract $raw_version; refusing." >&2
      return 1
      ;;
  esac

  if [[ "$verified" != "t" ]]; then
    echo "Contract $raw_version registry/constraints/policies/ACLs do not match its file." >&2
    return 1
  fi
}

previous=-1
for contract in "${CONTRACTS[@]}"; do
  filename="${contract##*/}"
  if [[ ! "$filename" =~ ^([0-9]{4})_([a-z0-9_]+)[.]sql$ ]]; then
    echo "Invalid contract filename: $filename" >&2
    exit 2
  fi
  raw_version="${BASH_REMATCH[1]}"
  version="$((10#$raw_version))"
  contract_sha256="$(sha256_file "$contract")"
  expected_registry_record="$filename|$contract_sha256"
  if [[ "$raw_version" != "0008" ]]; then
    echo "No postcondition verifier is defined for contract $raw_version; refusing before apply." >&2
    exit 2
  fi
  if (( version <= last_migration_version )); then
    echo "Contract $filename must sort after every regular migration." >&2
    exit 2
  fi
  if (( version <= previous )); then
    echo "Contract versions must be unique and strictly increasing: $filename" >&2
    exit 2
  fi
  previous="$version"

  registry_exists="$(
    "${PSQL[@]}" --tuples-only --no-align --command \
      "select to_regclass('fluyo_private.contract_migrations') is not null"
  )"
  registered_record=""
  if [[ "$registry_exists" == "t" ]]; then
    registered_record="$(
      "${PSQL[@]}" --tuples-only --no-align \
        --command \
        "select filename || '|' || sha256 from fluyo_private.contract_migrations
          where version = '$raw_version'"
    )"
  fi

  if [[ -n "$registered_record" ]]; then
    if [[ "$registered_record" != "$expected_registry_record" ]]; then
      echo "Registered contract $raw_version does not match the current filename/SHA-256." >&2
      exit 1
    fi
    verify_contract_effects "$raw_version" "$filename" "$contract_sha256"
    echo "==> Already registered: $filename"
    continue
  fi

  # Detect the only pre-registry contract state explicitly. Blindly re-running an old
  # manually applied contract would fail on duplicate constraints and obscure drift.
  if [[ "$raw_version" == "0008" ]]; then
    contract_effect_exists="$(
      "${PSQL[@]}" --tuples-only --no-align --command \
        "select exists (
           select 1 from pg_catalog.pg_constraint
           where conname = 'goal_deposits_rpc_state_required'
             and conrelid = 'public.goal_deposits'::regclass
         )"
    )"
    if [[ "$contract_effect_exists" == "t" ]]; then
      echo "Contract 0008 effects exist without their registry row; stop and reconcile drift." >&2
      exit 1
    fi
  fi

  echo "==> Applying contract atomically: $filename"
  "${PSQL[@]}" --set contract_sha256="$contract_sha256" \
    --single-transaction --file "$contract"

  registered_record="$(
    "${PSQL[@]}" --tuples-only --no-align \
      --command \
      "select filename || '|' || sha256 from fluyo_private.contract_migrations
        where version = '$raw_version'"
  )"
  if [[ "$registered_record" != "$expected_registry_record" ]]; then
    echo "Contract $filename committed without the expected registry row." >&2
    exit 1
  fi
  verify_contract_effects "$raw_version" "$filename" "$contract_sha256"
done

echo "==> Contract registry"
"${PSQL[@]}" --no-align --field-separator=' | ' --command \
  "select version, filename, sha256, applied_at, applied_by
     from fluyo_private.contract_migrations
    order by version"
