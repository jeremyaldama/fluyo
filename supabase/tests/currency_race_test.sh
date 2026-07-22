#!/usr/bin/env bash
# Reproduces the first-monetary-write/currency-update race with two PostgreSQL
# sessions. The update must wait for the insert transaction and then reject the
# relabel once that first monetary row becomes visible.
set -Eeuo pipefail

if [[ -z "${DATABASE_URL:-}" ]]; then
  echo "DATABASE_URL must point to the disposable migration-test database." >&2
  exit 2
fi

readonly USER_ID="10000000-0000-0000-0000-000000000099"
readonly AUTH_ID="00000000-0000-0000-0000-000000000099"
readonly APP_NAME="fluyo_currency_race_insert"
readonly INSERT_LOG="$(mktemp)"
readonly UPDATE_LOG="$(mktemp)"
readonly -a PSQL=(psql "$DATABASE_URL" --no-psqlrc --set ON_ERROR_STOP=1)

insert_pid=""
cleanup() {
  if [[ -n "$insert_pid" ]] && kill -0 "$insert_pid" 2>/dev/null; then
    kill "$insert_pid" 2>/dev/null || true
    wait "$insert_pid" 2>/dev/null || true
  fi
  rm -f -- "$INSERT_LOG" "$UPDATE_LOG"
}
trap cleanup EXIT

"${PSQL[@]}" --command "
  insert into auth.users (id) values ('$AUTH_ID');
  insert into public.users (id, auth_id, email, currency)
  values ('$USER_ID', '$AUTH_ID', 'currency-race@example.test', 'PEN');
"

"${PSQL[@]}" >"$INSERT_LOG" 2>&1 --command "
  select set_config('application_name', '$APP_NAME', false);
  begin;
  insert into public.expenses (user_id, amount, source, client_request_id)
  values ('$USER_ID', 1, 'manual', '50000000-0000-0000-0000-000000000099');
  select pg_sleep(8);
  commit;
" &
insert_pid=$!

race_ready=false
for _ in {1..100}; do
  if ! kill -0 "$insert_pid" 2>/dev/null; then
    break
  fi

  wait_event="$(
    "${PSQL[@]}" --tuples-only --no-align --command "
      select coalesce(max(wait_event), '')
      from pg_catalog.pg_stat_activity
      where application_name = '$APP_NAME';
    "
  )"
  if [[ "$wait_event" == "PgSleep" ]]; then
    race_ready=true
    break
  fi
  sleep 0.05
done

if [[ "$race_ready" != true ]]; then
  echo "The insert session never reached its post-insert synchronization point." >&2
  wait "$insert_pid" || true
  insert_pid=""
  sed -n '1,120p' "$INSERT_LOG" >&2
  exit 1
fi

set +e
"${PSQL[@]}" >"$UPDATE_LOG" 2>&1 --command "
  set statement_timeout = '15s';
  update public.users
  set currency = 'USD'
  where id = '$USER_ID';
"
update_status=$?
set -e

if ! wait "$insert_pid"; then
  insert_pid=""
  echo "The concurrent first monetary insert failed." >&2
  sed -n '1,120p' "$INSERT_LOG" >&2
  exit 1
fi
insert_pid=""

update_output="$(<"$UPDATE_LOG")"
if [[ "$update_status" -eq 0 ]]; then
  echo "A concurrent currency relabel committed after the first monetary write." >&2
  exit 1
fi
if [[ "$update_output" != *"Currency cannot change after monetary activity exists"* ]]; then
  echo "The currency update failed for an unexpected reason." >&2
  sed -n '1,120p' "$UPDATE_LOG" >&2
  exit 1
fi

final_state="$(
  "${PSQL[@]}" --tuples-only --no-align --command "
    select u.currency || '|' || count(e.id)::text
    from public.users as u
    left join public.expenses as e on e.user_id = u.id
    where u.id = '$USER_ID'
    group by u.currency;
  "
)"
if [[ "$final_state" != "PEN|1" ]]; then
  echo "Unexpected state after currency race test: $final_state" >&2
  exit 1
fi

"${PSQL[@]}" --command "
  delete from public.expenses where user_id = '$USER_ID';
  delete from public.users where id = '$USER_ID';
  delete from auth.users where id = '$AUTH_ID';
"

echo "Currency/first-write concurrency test passed"
