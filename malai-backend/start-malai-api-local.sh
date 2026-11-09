#!/usr/bin/env bash
#
# Run malai-api locally on :8080 — for use with the Vite dev server in malai-webapp, which
# calls http://localhost:8080 directly from http://localhost:5173 (VITE_API_BASE_URL in
# malai-webapp/.env.example), with credentials included.
#
# Also boots two helpers in the background:
#   - malai-stub on :9090 — simulates external banks (Confirmation of Payee federation,
#     FPS/SWIFT/BACS scheme rails). Without it, adding a payee whose sort code isn't in
#     our local DB fails with ECONNREFUSED on :9090.
#   - malai-cognito-stub on :9095 — a WireMock stand-in for AWS Cognito.
#     Register / login / refresh / sign-out all work offline; any confirmation or OTP
#     code is accepted (123456 after registering, 12345678 at login). Users and the JWT
#     signing key persist in ~/malai-cognito-stub so restarts don't log you out. `POST :9095/stub/reset`
#     forgets every user; `GET :9095/stub/users` lists them.
#
# Wiring:
#   - Postgres on localhost:5432 and Localstack SQS on localhost:4566, both started
#     separately by `start-docker-containers-local.sh`.
#   - Helper stdout/stderr stream to /tmp/malai-stub.log and /tmp/malai-cognito-stub.log;
#     tail them if a stub-side interaction looks off.
#
# Usage:
#   ./start-malai-api-local.sh                # rebuild jars + run
#   ./start-malai-api-local.sh --no-build     # skip rebuild, run existing -exec.jars
#
# Re-running this script is a clean restart: any malai-api on :8080, malai-stub on :9090
# and malai-cognito-stub on :9095 are stopped first.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$SCRIPT_DIR/malai-api/target/malai-api-DEV-SNAPSHOT-exec.jar"
STUB_JAR="$SCRIPT_DIR/malai-stub/target/malai-stub-DEV-SNAPSHOT-exec.jar"
COGNITO_STUB_JAR="$SCRIPT_DIR/malai-cognito-stub/target/malai-cognito-stub-DEV-SNAPSHOT-exec.jar"
STUB_LOG="/tmp/malai-stub.log"
COGNITO_STUB_LOG="/tmp/malai-cognito-stub.log"
PORT=8080
STUB_PORT=9090
COGNITO_STUB_PORT=9095

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

# 1. If anything is already on the ports we want, stop it. Treat this as a "make the port
#    mine" step — if the user has something unrelated bound to one of these they'd want to
#    know about it, so we log the PID + command before signalling.
stop_port() {
  local port=$1
  local existing_pid
  existing_pid=$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null | head -1 || true)
  [ -n "$existing_pid" ] || return 0
  local existing_cmd
  existing_cmd=$(ps -o command= -p "$existing_pid" 2>/dev/null || echo "<gone>")
  echo "==> Stopping existing process on :$port (pid $existing_pid: $existing_cmd)"
  kill "$existing_pid" 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 1
    lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1 || break
  done
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "==> Process on :$port didn't exit on SIGTERM, sending SIGKILL"
    kill -9 "$existing_pid" 2>/dev/null || true
    sleep 1
  fi
}
stop_port "$PORT"
stop_port "$STUB_PORT"
stop_port "$COGNITO_STUB_PORT"

# 2. Postgres and Localstack must be running locally. Don't auto-start to avoid surprising
#    the user — they own the lifecycle of the docker containers via
#    start-docker-containers-local.sh.
require_port() {
  local port=$1 name=$2
  if ! lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1; then
    echo "ERROR: Nothing listening on :$port ($name). Start the containers first:" >&2
    echo "         $SCRIPT_DIR/start-docker-containers-local.sh" >&2
    exit 1
  fi
}
require_port 5432 Postgres
require_port 4566 "Localstack SQS"

# 3. Build the executable jars unless explicitly skipped. -pl/-am builds the three
#    deployables plus only the modules they depend on, which is much faster than a full
#    reactor build.
if [ "$SKIP_BUILD" = false ]; then
  echo "==> Building malai-api + malai-stub + malai-cognito-stub (./mvnw install -pl malai-api,malai-stub,malai-cognito-stub -am -DskipTests)"
  "$SCRIPT_DIR/mvnw" install -pl malai-api,malai-stub,malai-cognito-stub -am -DskipTests -f "$SCRIPT_DIR/pom.xml" -q
fi

for jar in "$JAR" "$STUB_JAR" "$COGNITO_STUB_JAR"; do
  if [ ! -f "$jar" ]; then
    echo "ERROR: $jar not found. Run without --no-build to produce it." >&2
    exit 1
  fi
done

command -v java >/dev/null 2>&1 || { echo "ERROR: 'java' not on PATH" >&2; exit 1; }

# 4. Cognito wiring. The stub ignores client ids / secrets, but the properties are
#    mandatory so the values just need to exist. The issuer's last path segment doubles as
#    the user-pool id (same shape as a real Cognito issuer URL), and the SDK's endpoint
#    override sends every Cognito call to the stub instead of amazonaws.com.
export MAL_AWS_COGNITO_CLIENT_ID=local-client-id
export MAL_AWS_COGNITO_CLIENT_SECRET=local-client-secret
export MAL_AWS_COGNITO_JWT_TOKEN_ISSUER="http://localhost:$COGNITO_STUB_PORT/local-user-pool"
export MALAI_COGNITO_ENDPOINTOVERRIDE="http://localhost:$COGNITO_STUB_PORT"
export MAL_AWS_COGNITO_USER_POOL_ID="${MAL_AWS_COGNITO_JWT_TOKEN_ISSUER##*/}"

export MAL_AWS_COGNITO_MALAI_SERVICE_CLIENT_ID=local-service-client-id
export MAL_AWS_COGNITO_MALAI_SERVICE_CLIENT_SECRET=local-service-client-secret
export MAL_AWS_COGNITO_MALAI_SERVICE_TOKEN_ENDPOINT_URL="http://localhost:$COGNITO_STUB_PORT/oauth2/token"

# The Admin* Cognito client and the SQS client both sign requests with the SDK's default
# credentials chain. Neither the stub nor localstack validates the signature, but the SDK
# refuses to send anything without credentials, so provide throwaway ones and drop any
# leftover session or profile from the shell.
export AWS_ACCESS_KEY_ID=local
export AWS_SECRET_ACCESS_KEY=local
export AWS_REGION=us-east-1
unset AWS_SESSION_TOKEN AWS_PROFILE

# 5. Wire env vars for malai-api. The stubs don't need any of these — their defaults are
# fine for local dev (ports 9090 / 9095, no AWS, ambient payroll disabled).
# malai_local is the dev DB — preserved across runs of start-docker-containers-local.sh and
# never touched by the test suite (which targets malai_test). Wipe with `./malai-db.sh local`
# if you need a fresh start.
export DATABASE_URL=jdbc:postgresql://localhost:5432/malai_local
export DATABASE_USERNAME=malai
export DATABASE_PASSWORD=malai

export MAL_BANK_SORT_CODE="65-82-37"
export MAL_BANK_BIC="MALAGB22XXX"

export MAL_CORS_ALLOWED_ORIGINS="http://localhost:5173"

# Safari refuses to store cookies with the `Secure` flag over `http://localhost`. Chrome
# and Firefox grant localhost an exception; WebKit does not. Without this override the
# webapp logs in successfully (200) but the next call has no `access_token` cookie and
# 401s. The default stays `true` via application.properties.
export MALAI_AUTH_COOKIES_SECURE=false

export SPRING_CLOUD_AWS_SQS_ENDPOINT="http://localhost:4566"

# 6. Boot the helpers in the background. When the api foreground process exits (Ctrl+C,
#    normal stop, or crash), tear them down too — otherwise they linger until the next run.
HELPER_PIDS=()
stop_helpers() {
  for pid in "${HELPER_PIDS[@]:-}"; do
    [ -n "$pid" ] || continue
    echo "==> Stopping helper (pid $pid)"
    kill "$pid" 2>/dev/null || true
    wait "$pid" 2>/dev/null || true
  done
}
trap stop_helpers EXIT

start_helper() {
  local name=$1 jar=$2 port=$3 log=$4
  echo "==> Starting $name on :$port (logs: $log)"
  java -jar "$jar" >"$log" 2>&1 &
  local pid=$!
  HELPER_PIDS+=("$pid")

  echo "==> Waiting for $name on :$port"
  for _ in $(seq 1 30); do
    if ! kill -0 "$pid" 2>/dev/null; then
      echo "ERROR: $name exited before binding :$port — tail $log" >&2
      exit 1
    fi
    lsof -nP -iTCP:"$port" -sTCP:LISTEN >/dev/null 2>&1 && return 0
    sleep 1
  done
  echo "ERROR: $name did not start on :$port within 30s — tail $log" >&2
  exit 1
}

start_helper malai-stub "$STUB_JAR" "$STUB_PORT" "$STUB_LOG"
export MAL_COGNITO_STUB_PORT="$COGNITO_STUB_PORT"
export MAL_COGNITO_STUB_ISSUER="$MAL_AWS_COGNITO_JWT_TOKEN_ISSUER"
start_helper malai-cognito-stub "$COGNITO_STUB_JAR" "$COGNITO_STUB_PORT" "$COGNITO_STUB_LOG"

echo "==> Starting malai-api on :$PORT — Ctrl+C to stop"
java -jar "$JAR"
