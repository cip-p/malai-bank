package com.malai.test.acceptance.kensa.fixtures

import com.malai.sql.MalaiSchema
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import java.sql.Connection

class SharedDatabaseHandBackTest {

    @Test
    fun `hands the database back at post-migration state when the suite truncated it`() {
        MalaiAcceptanceTest.cleanTables()

        SharedDatabaseHandBack.handBack()

        assertThat(rowsTheMigrationsSeed()).isEqualTo(POST_MIGRATION_STATE)
    }

    @Test
    fun `leaves the database alone when this JVM never truncated it`() {
        connection().use { it.deleteAllFixtures() }

        SharedDatabaseHandBack.handBack()

        assertThat(rowsTheMigrationsSeed()).isEqualTo(NOTHING)
    }

    @Test
    fun `hands back the state a Flyway clean and migrate would have produced`() {
        MalaiSchema.resetToMigratedState()
        val postMigration = rowCountsPerTable()

        MalaiAcceptanceTest.cleanTables()
        SharedDatabaseHandBack.handBack()

        assertThat(rowCountsPerTable()).isEqualTo(postMigration)
    }

    private fun rowCountsPerTable(): Map<String, Int> = connection().use { connection ->
        connection.stringsFrom(
            "SELECT tablename FROM pg_tables WHERE schemaname = 'public' ORDER BY tablename"
        ).associateWith { table ->
            connection.createStatement().executeQuery("SELECT count(*) FROM $table").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }
    }

    private fun rowsTheMigrationsSeed() = connection().use { connection ->
        MigrationSeededRows(
            userEmails = connection.stringsFrom("SELECT email FROM users"),
            accountNames = connection.stringsFrom("SELECT name FROM accounts"),
            ledgerAccountIds = connection.stringsFrom("SELECT account_id FROM ledger_accounts"),
        )
    }

    private fun Connection.stringsFrom(sql: String): List<String> =
        createStatement().executeQuery(sql).use { rows ->
            generateSequence { if (rows.next()) rows.getString(1) else null }.sorted().toList()
        }

    private data class MigrationSeededRows(
        val userEmails: List<String>,
        val accountNames: List<String>,
        val ledgerAccountIds: List<String>,
    )

    companion object {

        private val POST_MIGRATION_STATE = MigrationSeededRows(
            userEmails = listOf("system+clearing@malai-internal.invalid"),
            accountNames = listOf("BACS Inbound Suspense", "FPS Inbound Suspense", "Scheme Settlement"),
            ledgerAccountIds = listOf(
                "00000000-0000-4000-8001-000000010002",
                "00000000-0000-4000-8001-000000010003",
                "00000000-0000-4000-8001-000000010005",
            ),
        )

        private val NOTHING = MigrationSeededRows(emptyList(), emptyList(), emptyList())

        private fun connection() =
            DatabaseConnection.createConnection(MalaiAcceptanceTestContext.DATABASE_URL)

        @JvmStatic
        @AfterAll
        fun handTheSharedDatabaseBack() = MalaiSchema.resetToMigratedState()
    }
}
