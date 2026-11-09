#!/usr/bin/env bash
#
# Start (or restart) the local Postgres container used by both the test suite and
# `start-malai-api-local.sh`. Two databases live in this container:
#
#   - malai_test  — wiped from scratch on every run via `malai-db.sh` (last step below).
#                   Tests point here by default; safe to nuke at will.
#   - malai_local — created on first run, preserved on subsequent runs. The local
#                   `start-malai-api-local.sh` points here so webapp dev data survives
#                   tests + container restarts. Wipe explicitly via `malai-db.sh local`.
#
# Both databases are created `IF NOT EXISTS`, so re-running this script is a no-op for the
# DBs themselves — the only data loss happens in `malai_test` (deliberately).
set -e

POSTGRES_CONTAINER="malai-postgres"
POSTGRES_USER="malai"
LOCALSTACK_CONTAINER="malai-localstack"

stop_and_remove_container(){
    local container_name=$1
    echo " --------- Stopping and removing container $container_name ---------"
    docker rm --force "$container_name" >/dev/null 2>&1 || true
    echo "Container $container_name removed"
}

start_postgresql() {
  echo ""
  local POSTGRESQL_DATA_DIR="$HOME/malai-postgresql"
  mkdir -p $POSTGRESQL_DATA_DIR

  stop_and_remove_container $POSTGRES_CONTAINER
  echo "Starting postgresql container $POSTGRES_CONTAINER"
  # POSTGRES_DB seeds an initial DB on first init only; on re-runs the bind-mounted data
  # dir already has its catalog so this is ignored. We don't depend on it — both DBs we
  # actually use are created explicitly in `ensure_databases`.
  docker run -d --rm \
    --name $POSTGRES_CONTAINER \
    -p 5432:5432 \
    -e POSTGRES_DB=$POSTGRES_USER \
    -e POSTGRES_USER=$POSTGRES_USER \
    -e POSTGRES_PASSWORD=$POSTGRES_USER \
    -v $POSTGRESQL_DATA_DIR:/var/lib/postgresql/data \
    postgres:17

  echo "Waiting for postgresql to be ready"
  until docker exec $POSTGRES_CONTAINER pg_isready -U $POSTGRES_USER >/dev/null 2>&1; do
    echo -n '.'
    sleep 1
  done

  echo ""
  echo "Postgresql is up and running"
}

# Postgres has no `CREATE DATABASE IF NOT EXISTS`, so check the system catalog first.
# Idempotent: skipping when the DB already exists is what preserves `malai_local` data
# across re-runs of this script.
ensure_database() {
    local db=$1
    if docker exec $POSTGRES_CONTAINER psql -U $POSTGRES_USER -tAc \
            "SELECT 1 FROM pg_database WHERE datname = '$db'" | grep -q 1; then
        echo "Database '$db' already exists — leaving as-is."
    else
        echo "Creating database '$db'"
        docker exec $POSTGRES_CONTAINER psql -U $POSTGRES_USER -c "CREATE DATABASE $db"
    fi
}

start_localstack() {
  echo ""
  stop_and_remove_container $LOCALSTACK_CONTAINER
  echo "Starting localstack container $LOCALSTACK_CONTAINER (SQS only)"
  # SERVICES restricted to sqs keeps the cold-start fast and avoids pulling the
  # full localstack image. Wipe-on-rerun: --rm + restart-from-scratch gives the
  # same posture as malai_test (queues are recreated on every run).
  # --cpus bounds the blast radius: this container has wedged under an SQS receive
  # storm before, spinning ~2 cores indefinitely while its health endpoint was
  # unresponsive — enough to thermally throttle the laptop with the lid shut. The
  # cap is well above what SQS-only needs, so it only bites on a repeat of that.
  # The image is pinned rather than floating on `:3` so the broker every test JVM talks
  # to is the exact one CI pulls; bump it deliberately, in all three places at once.
  docker run -d --rm \
    --name $LOCALSTACK_CONTAINER \
    --cpus 2 \
    -p 4566:4566 \
    -e SERVICES=sqs \
    -e EAGER_SERVICE_LOADING=1 \
    localstack/localstack:3.8.1

  echo "Waiting for localstack to be ready"
  until curl -s http://localhost:4566/_localstack/health | jq -e '.services.sqs == "available"' >/dev/null; do
    echo -n '.'
    sleep 1
  done

  echo ""
  echo "Localstack is up (SQS ready)"
  # Queues are NOT provisioned here. payments-service, malai-stub, and malai-api each run
  # the always-on QueueProvisioner on startup, which create-if-absents every payments
  # catalog queue + paired DLQ idempotently. Acceptance tests boot with a per-JVM
  # queue-name-prefix so each test JVM gets its own isolated queue set on this same broker,
  # and QueueDeprovisioner deletes that set when the JVM's context closes — otherwise every
  # `./mvnw install` leaves its sets behind, and Localstack walks every queue it holds once a
  # second — the pile-up is the likeliest fuel for the receive storm above.
}

start_postgresql
start_localstack

ensure_database malai_test
ensure_database malai_local

# Tests assume a freshly-migrated `malai_test`. Local DB is left untouched here — the
# malai-api JVM runs Flyway on boot (FlywayMigrationConfig) so its schema stays in sync
# with the migrations as a side-effect of starting the app.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"$SCRIPT_DIR/malai-db.sh"
