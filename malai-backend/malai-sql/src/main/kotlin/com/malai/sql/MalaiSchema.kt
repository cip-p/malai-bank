package com.malai.sql

import org.flywaydb.core.Flyway
import java.sql.DriverManager

object MalaiSchema {

    const val TEST_DATABASE = "malai_test"
    const val DEFAULT_TEST_URL = "jdbc:postgresql://localhost:5432/$TEST_DATABASE"
    const val DEFAULT_USER = "malai"
    const val DEFAULT_PASSWORD = "malai"

    private const val TEST_APP_PASSWORD = "test-malai-app-password"
    private const val MIGRATION_LOCATION = "db/migration"
    private const val SEED_MARKER = "seed"

    fun flyway(
        url: String,
        user: String,
        password: String,
        malaiAppPassword: String,
        dbMasterUsername: String,
        cleanEnabled: Boolean,
    ): Flyway = Flyway.configure()
        .dataSource(url, user, password)
        .cleanDisabled(!cleanEnabled)
        .placeholders(
            mapOf(
                "malai_app_password" to malaiAppPassword,
                "db_master_username" to dbMasterUsername,
            )
        )
        .load()

    fun resetToMigratedState() = resetToMigratedState(
        url = System.getenv("DATABASE_URL") ?: DEFAULT_TEST_URL,
        user = System.getenv("DATABASE_USERNAME") ?: DEFAULT_USER,
        password = System.getenv("DATABASE_PASSWORD") ?: DEFAULT_PASSWORD,
    )

    fun resetToMigratedState(url: String, user: String, password: String) {
        refuseAnythingButTheTestDatabase(url)

        val flyway = flyway(
            url = url,
            user = user,
            password = password,
            malaiAppPassword = TEST_APP_PASSWORD,
            dbMasterUsername = user,
            cleanEnabled = true,
        )

        flyway.clean()
        flyway.migrate()
    }

    fun restoreSeededRows() = restoreSeededRows(
        url = System.getenv("DATABASE_URL") ?: DEFAULT_TEST_URL,
        user = System.getenv("DATABASE_USERNAME") ?: DEFAULT_USER,
        password = System.getenv("DATABASE_PASSWORD") ?: DEFAULT_PASSWORD,
    )

    fun restoreSeededRows(url: String, user: String, password: String) {
        refuseAnythingButTheTestDatabase(url)

        val seedMigrations = seedMigrationScripts(url, user, password)

        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().use { statement ->
                seedMigrations.forEach { statement.execute(sqlOf(it)) }
            }
        }
    }

    private fun seedMigrationScripts(url: String, user: String, password: String): List<String> =
        flyway(
            url = url,
            user = user,
            password = password,
            malaiAppPassword = TEST_APP_PASSWORD,
            dbMasterUsername = user,
            cleanEnabled = false,
        ).info().all()
            .mapNotNull { it.script }
            .filter { it.endsWith(".sql") && it.contains(SEED_MARKER) }

    private fun sqlOf(script: String): String =
        javaClass.getResourceAsStream("/$MIGRATION_LOCATION/$script")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Flyway lists '$script' but it is not on the classpath at '$MIGRATION_LOCATION'")

    fun refuseAnythingButTheTestDatabase(url: String) {
        val database = url.substringAfterLast('/').substringBefore('?')
        require(database == TEST_DATABASE) {
            "refusing to reset '$database': these helpers drop or rewrite the contents of the " +
                "schema and are only allowed against '$TEST_DATABASE'"
        }
    }
}
