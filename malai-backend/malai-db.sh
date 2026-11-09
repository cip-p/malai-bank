#!/usr/bin/env bash
#
# Drop + recreate one of the two local Postgres databases by running Flyway with `clean`
# followed by `migrate`. Targets:
#
#   ./malai-db.sh           # default → malai_test (what tests + CI need)
#   ./malai-db.sh test      # explicit form of the default
#   ./malai-db.sh local     # ⚠️  wipes malai_local — deliberate, for resetting the dev DB
#
# The DBs themselves must already exist (created by `start-docker-containers-local.sh`).
# This script only operates on the schema inside them.
#
# CI also runs this — the `build` job in .github/workflows/malai-backend.yml runs
# `./malai-db.sh` with no args, expecting the test DB.
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

target="${1:-test}"
case "$target" in
    test)  db="malai_test" ;;
    local) db="malai_local" ;;
    *)
        echo "ERROR: unknown target '$target' (try 'test' or 'local')" >&2
        exit 1
        ;;
esac

# DATABASE_URL is the override the migrator reads (see MalaiDbMigrator.kt). We compute a
# default per-target here, but still allow an externally-set override for unusual setups
# (e.g. GitLab CI sets DATABASE_URL pointing at the `postgres` service container, not
# localhost). MALAI_DB_CLEAN=true tells the migrator to `flyway.clean()` before
# `flyway.migrate()` — the "from scratch" semantic the script implies.
echo "Recreating Malai database '$db' from scratch..."
DATABASE_URL="${DATABASE_URL:-jdbc:postgresql://localhost:5432/$db}" \
MALAI_DB_CLEAN=true \
"$SCRIPT_DIR/mvnw" clean compile -q -f "$SCRIPT_DIR/pom.xml" -pl malai-sql exec:java \
  -Dexec.mainClass="com.malai.sql.MalaiDbMigratorKt" \
  -Dexec.classpathScope=runtime
