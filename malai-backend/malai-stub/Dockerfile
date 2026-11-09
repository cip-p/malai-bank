# malai-stub: separate Spring Boot deployable that simulates the outside
# world (FPS / SWIFT / BACS scheme integrations, the card schemes, and the
# Confirmation-of-Payee directory). Not bundled into malai-api.
#
# Built by malai-end-to-end-tests/docker-compose.e2e.yml with the repository root
# as the build context, like malai-api — hence `malai-stub/target/...` below rather
# than `target/...`.
FROM eclipse-temurin:25-jre-alpine

WORKDIR /app
COPY malai-stub/target/malai-stub-DEV-SNAPSHOT-exec.jar /app/malai-stub.jar

EXPOSE 9090

ENTRYPOINT ["java", "-jar", "/app/malai-stub.jar"]
