#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE_FILE="$SCRIPT_DIR/docker-compose.e2e.yml"

export MAVEN_OPTS="${MAVEN_OPTS:-} --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow"

echo "===== Building the backend JARs ====="
"$PROJECT_DIR/mvnw" clean package -DskipTests -q -f "$PROJECT_DIR/pom.xml"

echo ""
echo "===== Starting PostgreSQL ====="
docker compose -f "$COMPOSE_FILE" up -d --build --wait postgres

echo ""
echo "===== Running database migrations ====="
# We use exec:exec (forks a fresh JVM with a plain -classpath) rather than exec:java (runs
# in-process with a custom Maven ClassRealm). On Java 25 + exec-maven-plugin 3.6.3, exec:java
# sometimes partially module-loads Flyway's split jars, which makes Flyway's own internal
# classes (org.flywaydb.core.internal.util.ExceptionUtils) unreachable from sibling classes
# during clean()/migrate(). exec:exec sidesteps the whole classrealm/module-path story.
DATABASE_URL="jdbc:postgresql://localhost:5433/malai" \
DATABASE_USERNAME=malai \
DATABASE_PASSWORD=malai \
"$PROJECT_DIR/mvnw" clean compile -q -f "$PROJECT_DIR/pom.xml" -pl malai-sql exec:exec \
  -Dexec.executable=java \
  -Dexec.classpathScope=runtime \
  -Dexec.args="-classpath %classpath com.malai.sql.MalaiDbMigratorKt"

echo ""
echo "===== Starting malai-api + supporting containers ====="
docker compose -f "$COMPOSE_FILE" up -d --build --force-recreate --wait malai-api malai-stub localstack

echo ""
echo "===== E2E environment is ready ====="
echo "  PostgreSQL:       localhost:5433"
echo "  Malai API:        localhost:8080"
echo "  Malai Stub:       localhost:9091"
echo "  Localstack (SQS): localhost:4567"
echo ""
echo "WireMock runs in the test JVM on localhost:9090 (malai-api reaches it at host.docker.internal:9090)."
echo "Run tests with: ./mvnw test -pl malai-end-to-end-tests -De2e.skip=false"
