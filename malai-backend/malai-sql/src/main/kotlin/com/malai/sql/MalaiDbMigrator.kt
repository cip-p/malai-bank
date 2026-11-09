package com.malai.sql

fun main() {
    val url = System.getenv("DATABASE_URL") ?: MalaiSchema.DEFAULT_TEST_URL
    val user = System.getenv("DATABASE_USERNAME") ?: MalaiSchema.DEFAULT_USER
    val password = System.getenv("DATABASE_PASSWORD") ?: MalaiSchema.DEFAULT_PASSWORD
    val cleanFirst = System.getenv("MALAI_DB_CLEAN") == "true"

    // Phase 2 D-2.12: V11 (create_malai_app_role) substitutes two Flyway
    // placeholders. Spring Boot wires these from MAL_APP_DB_PASSWORD /
    // MAL_DB_MASTER_USERNAME via spring.flyway.placeholders.*; the standalone
    // runner mirrors that here so `start-docker-containers-local.sh` and any
    // direct `mvn -pl malai-sql exec:java` invocation work without Spring.
    // Test-only fallbacks match malai-{api,services}/src/test/resources/
    // application-test.properties.
    val malaiAppPassword = System.getenv("MAL_APP_DB_PASSWORD") ?: "test-malai-app-password"
    val dbMasterUsername = System.getenv("MAL_DB_MASTER_USERNAME") ?: user

    val flyway = MalaiSchema.flyway(
        url = url,
        user = user,
        password = password,
        malaiAppPassword = malaiAppPassword,
        dbMasterUsername = dbMasterUsername,
        cleanEnabled = cleanFirst,
    )

    if (cleanFirst) {
        println("MALAI_DB_CLEAN=true — cleaning database before migrating...")
        flyway.clean()
    }

    println("Running migrations...")
    val result = flyway.migrate()

    println("Applied ${result.migrationsExecuted} migration(s) successfully.")
}
