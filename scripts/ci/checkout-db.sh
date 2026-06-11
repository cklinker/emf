#!/usr/bin/env bash
# Check out a throwaway PG schema from the kelta-ci-db pool.
#
# Picks one of N pool instances (round-robin via $RANDOM by default; set
# CI_DB_INSTANCE=N to pin), creates a unique schema, and emits a JDBC URL
# + matching env vars on stdout for sourcing.
#
# Usage (in a CI step):
#   eval "$(scripts/ci/checkout-db.sh)"
#   # ... run tests against $CI_DB_JDBC_URL ...
#   scripts/ci/release-db.sh
#
# Env (override defaults):
#   CI_DB_NAMESPACE   default kelta-ci-db
#   CI_DB_SVC         default kelta-ci-db (headless service)
#   CI_DB_USER        default ci         (matches kelta-ci-db secret)
#   CI_DB_PASSWORD    default fetched from secret if unset
#   CI_DB_DATABASE    default ci
#   CI_DB_INSTANCE    pin to instance ordinal 0..N-1 (default: random)
#   CI_DB_POOL_SIZE   default 4 (matches statefulset replicas)
#
# Output (stdout): shell assignments suitable for `eval`. stderr = log.
#
# Side effects:
#   - Creates schema "ci_<run_id>" on the chosen instance
#   - Writes /tmp/ci-db-checkout.env with the chosen instance + schema for
#     release-db.sh to consume

set -euo pipefail

CI_DB_NAMESPACE="${CI_DB_NAMESPACE:-kelta-ci-db}"
CI_DB_SVC="${CI_DB_SVC:-kelta-ci-db}"
CI_DB_USER="${CI_DB_USER:-ci}"
CI_DB_DATABASE="${CI_DB_DATABASE:-ci}"
CI_DB_POOL_SIZE="${CI_DB_POOL_SIZE:-4}"

if [[ -z "${CI_DB_PASSWORD:-}" ]]; then
  CI_DB_PASSWORD="$(kubectl -n "$CI_DB_NAMESPACE" get secret kelta-ci-db-credentials \
    -o jsonpath='{.data.POSTGRES_PASSWORD}' | base64 --decode)"
fi

# Pick instance ordinal.
if [[ -n "${CI_DB_INSTANCE:-}" ]]; then
  ord="$CI_DB_INSTANCE"
else
  ord=$(( RANDOM % CI_DB_POOL_SIZE ))
fi

# Use the fully qualified .svc.cluster.local form. The bare ".svc" works
# inside k8s pods (DNS search domains + ndots:5) but the JDBC URL below is
# also consumed by service containers spawned via the host docker daemon,
# whose embedded resolver uses ndots:0 → no search-domain expansion. Without
# the .cluster.local suffix those containers fail with UnknownHostException.
host="${CI_DB_SVC}-${ord}.${CI_DB_SVC}.${CI_DB_NAMESPACE}.svc.cluster.local"
port=5432

# Unique schema per run. GITHUB_RUN_ID + GITHUB_RUN_ATTEMPT is unique within
# a workflow; fall back to PID + epoch for local invocation.
run_tag="${GITHUB_RUN_ID:-$$}_${GITHUB_RUN_ATTEMPT:-$(date +%s)}"
schema="ci_$(printf '%s' "$run_tag" | tr -c 'A-Za-z0-9_' '_')"

echo "[checkout-db] picked instance=$ord host=$host schema=$schema" >&2

PGPASSWORD="$CI_DB_PASSWORD" psql \
  -h "$host" -p "$port" -U "$CI_DB_USER" -d "$CI_DB_DATABASE" \
  -v ON_ERROR_STOP=1 \
  -c "CREATE SCHEMA IF NOT EXISTS \"$schema\" AUTHORIZATION \"$CI_DB_USER\";" \
  -c "GRANT ALL ON SCHEMA \"$schema\" TO \"$CI_DB_USER\";" >&2

# Persist the chosen instance + schema for release-db.sh.
cat > /tmp/ci-db-checkout.env <<EOF
CI_DB_HOST=$host
CI_DB_PORT=$port
CI_DB_USER=$CI_DB_USER
CI_DB_PASSWORD=$CI_DB_PASSWORD
CI_DB_DATABASE=$CI_DB_DATABASE
CI_DB_SCHEMA=$schema
CI_DB_INSTANCE=$ord
EOF

# Emit env-var assignments suitable for eval.
cat <<EOF
export CI_DB_HOST='$host'
export CI_DB_PORT='$port'
export CI_DB_USER='$CI_DB_USER'
export CI_DB_PASSWORD='$CI_DB_PASSWORD'
export CI_DB_DATABASE='$CI_DB_DATABASE'
export CI_DB_SCHEMA='$schema'
export CI_DB_JDBC_URL='jdbc:postgresql://${host}:${port}/${CI_DB_DATABASE}?currentSchema=${schema}'
EOF
