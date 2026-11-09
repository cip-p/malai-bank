#!/usr/bin/env bash
#
# Run the malai-seeder against your LOCAL Postgres (`malai_local`). Companion to
# `start-malai-api-local.sh` — both wire the same env, so once you've seeded with
# this script the demo users log into your locally-running malai-api unchanged.
#
# Wiring:
#   - Postgres on localhost:5432, db=malai_local (NOT malai_test). Start the
#     container separately via `start-docker-containers-local.sh`.
#   - Cognito: the local malai-cognito-stub on :9095. If `start-malai-api-local.sh`
#     already has it running the seeder just talks to it; otherwise this script boots
#     one for the duration of the run, so seeded users land in the same
#     ~/malai-cognito-stub state the API reads later.
#
# Usage:
#   ./demo-reset-local.sh                        # rebuild jars + run `reset`
#   ./demo-reset-local.sh --no-build             # skip rebuild
#   CMD=verify-chain ./demo-reset-local.sh       # non-destructive audit check
#   CMD=clean        ./demo-reset-local.sh       # delete Sarah's rows only
#   CMD=seed         ./demo-reset-local.sh       # seed without cleaning
#   CMD="seed-user --email a@b.com" ./demo-reset-local.sh   # seed an already-registered user
#
# Env overrides:
#   CMD — seeder subcommand (reset | clean | migrate | seed | verify-chain | "seed-user --email <email>"). Default: reset.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/malai-seeder/target/malai-seeder-DEV-SNAPSHOT-exec.jar"
COGNITO_STUB_JAR="$SCRIPT_DIR/malai-cognito-stub/target/malai-cognito-stub-DEV-SNAPSHOT-exec.jar"
COGNITO_STUB_LOG="/tmp/malai-cognito-stub.log"
COGNITO_STUB_PORT=9095
CMD="${CMD:-reset}"

SKIP_BUILD=false
for arg in "$@"; do
  case "$arg" in
    --no-build) SKIP_BUILD=true ;;
    -h|--help)
      sed -n '2,/^$/p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *) echo "unknown arg: $arg (try --help)" >&2; exit 1 ;;
  esac
done

# 1. Postgres must be running locally. Don't auto-start — the user owns the
#    docker container lifecycle via start-docker-containers-local.sh, same as
#    start-malai-api-local.sh.
if ! lsof -nP -iTCP:5432 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "ERROR: Nothing listening on :5432. Start Postgres first:" >&2
  echo "         $SCRIPT_DIR/start-docker-containers-local.sh" >&2
  exit 1
fi

# 2. Build the exec jars unless explicitly skipped. -pl/-am builds malai-seeder (and
#    the Cognito stub it may need) plus only the modules they depend on, much faster
#    than a full reactor build.
if [ "$SKIP_BUILD" = false ]; then
  echo "==> Building malai-seeder + malai-cognito-stub (./mvnw install -pl malai-seeder,malai-cognito-stub -am -DskipTests)"
  "$SCRIPT_DIR/mvnw" install -pl malai-seeder,malai-cognito-stub -am -DskipTests -f "$SCRIPT_DIR/pom.xml" -q
fi

if [ ! -f "$JAR" ]; then
  echo "ERROR: $JAR not found. Run without --no-build to produce it." >&2
  exit 1
fi

command -v java >/dev/null 2>&1 || { echo "ERROR: 'java' not on PATH" >&2; exit 1; }

STUB_PID=""
stop_stub() {
  [ -n "$STUB_PID" ] || return 0
  echo "==> Stopping malai-cognito-stub (pid $STUB_PID)"
  kill "$STUB_PID" 2>/dev/null || true
  wait "$STUB_PID" 2>/dev/null || true
}
trap stop_stub EXIT

# 3. Point Cognito at the local stub. The issuer's last path segment is the user-pool id.
issuer="http://localhost:$COGNITO_STUB_PORT/local-user-pool"
export MALAI_COGNITO_ENDPOINTOVERRIDE="http://localhost:$COGNITO_STUB_PORT"
export MAL_AWS_COGNITO_JWT_TOKEN_ISSUER="$issuer"
export MAL_AWS_COGNITO_USER_POOL_ID="${issuer##*/}"
# The SDK signs Admin* calls with the default credentials chain; the stub never checks
# the signature but the SDK won't send without credentials. Drop any leftover session
# or profile from the shell so it can't shadow these.
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export AWS_REGION=us-east-1
unset AWS_SESSION_TOKEN AWS_PROFILE

if lsof -nP -iTCP:"$COGNITO_STUB_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "==> Reusing the malai-cognito-stub already listening on :$COGNITO_STUB_PORT"
else
  if [ ! -f "$COGNITO_STUB_JAR" ]; then
    echo "ERROR: $COGNITO_STUB_JAR not found. Run without --no-build to produce it." >&2
    exit 1
  fi
  echo "==> Starting malai-cognito-stub on :$COGNITO_STUB_PORT for this run (logs: $COGNITO_STUB_LOG)"
  MAL_COGNITO_STUB_PORT="$COGNITO_STUB_PORT" MAL_COGNITO_STUB_ISSUER="$issuer" \
    java -jar "$COGNITO_STUB_JAR" >"$COGNITO_STUB_LOG" 2>&1 &
  STUB_PID=$!
  for _ in $(seq 1 30); do
    if ! kill -0 "$STUB_PID" 2>/dev/null; then
      echo "ERROR: malai-cognito-stub exited before binding :$COGNITO_STUB_PORT — tail $COGNITO_STUB_LOG" >&2
      exit 1
    fi
    lsof -nP -iTCP:"$COGNITO_STUB_PORT" -sTCP:LISTEN >/dev/null 2>&1 && break
    sleep 1
  done
  if ! lsof -nP -iTCP:"$COGNITO_STUB_PORT" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "ERROR: malai-cognito-stub did not start on :$COGNITO_STUB_PORT within 30s — tail $COGNITO_STUB_LOG" >&2
    exit 1
  fi
fi

# 4. Wire env vars and run. malai_local is the dev DB that start-malai-api-local.sh
#    also points at, so the seed lands in the same place the locally-running API reads from.
#    Master user/password = malai/malai (the docker Postgres runs one role for
#    everything, which the MigrateCommand's malai_app_password fallback already handles).
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/malai_local"
export MAL_DB_MASTER_USERNAME="malai"
export MAL_DB_MASTER_PASSWORD="malai"

export MAL_BANK_SORT_CODE="65-82-37"
export MAL_BANK_BIC="MALAGB22XXX"

echo "==> Running malai-seeder: cmd=$CMD db=malai_local cognitoPoolId=$MAL_AWS_COGNITO_USER_POOL_ID"
# Word-split CMD into argv so multi-token subcommands like
# `seed-user --email a@b.com` reach picocli as separate arguments.
read -ra CMD_ARGV <<< "$CMD"
set +e
java -jar "$JAR" "${CMD_ARGV[@]}"
exit_code=$?
set -e
exit "$exit_code"
