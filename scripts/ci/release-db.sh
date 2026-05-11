#!/usr/bin/env bash
# Release the schema claimed by checkout-db.sh.
#
# Reads /tmp/ci-db-checkout.env, drops the schema (CASCADE), exits cleanly.
# Safe to run more than once; missing schema is treated as already released.
#
# Usage (in a CI step, typically with `if: always()` so a failed test still
# releases its schema):
#   scripts/ci/release-db.sh

set -uo pipefail

ENV_FILE="${CI_DB_ENV_FILE:-/tmp/ci-db-checkout.env}"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "[release-db] no $ENV_FILE — checkout never ran or already cleaned up; nothing to do" >&2
  exit 0
fi

# shellcheck source=/dev/null
. "$ENV_FILE"

if [[ -z "${CI_DB_SCHEMA:-}" ]]; then
  echo "[release-db] $ENV_FILE missing CI_DB_SCHEMA; nothing to do" >&2
  rm -f "$ENV_FILE"
  exit 0
fi

echo "[release-db] dropping schema $CI_DB_SCHEMA on instance ${CI_DB_INSTANCE:-?}" >&2
PGPASSWORD="$CI_DB_PASSWORD" psql \
  -h "$CI_DB_HOST" -p "$CI_DB_PORT" -U "$CI_DB_USER" -d "$CI_DB_DATABASE" \
  -v ON_ERROR_STOP=1 \
  -c "DROP SCHEMA IF EXISTS \"$CI_DB_SCHEMA\" CASCADE;" >&2 \
  || echo "[release-db] DROP SCHEMA failed (instance may be down); leaving for next sweep" >&2

rm -f "$ENV_FILE"
