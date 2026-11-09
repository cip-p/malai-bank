package com.malai.sql

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.DriverManager
import java.util.UUID

class MalaiSchemaTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun startFromAMigratedDatabase() {
            MalaiSchema.resetToMigratedState()
        }
    }

    private val url = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/malai_test"
    private val user = System.getenv("DATABASE_USERNAME") ?: "malai"
    private val password = System.getenv("DATABASE_PASSWORD") ?: "malai"

    @Test
    fun `reset discards rows written after the migrations ran`() {
        insertUser("left-behind-${UUID.randomUUID()}@example.invalid")

        MalaiSchema.resetToMigratedState(url, user, password)

        assertThat(countUsersMatching("left-behind-%")).isZero()
    }

    @Test
    fun `reset restores the system rows the migrations seed`() {
        DriverManager.getConnection(url, user, password).use { it.createStatement().execute("TRUNCATE TABLE ledger_accounts CASCADE") }

        MalaiSchema.resetToMigratedState(url, user, password)

        assertThat(registeredLedgerAccounts()).containsExactlyInAnyOrder(
            "00000000-0000-4000-8001-000000010002",
            "00000000-0000-4000-8001-000000010003",
            "00000000-0000-4000-8001-000000010005",
        )
    }

    @Test
    fun `reset locates the test database the way the migrator CLI and CI do`() {
        insertUser("left-behind-${UUID.randomUUID()}@example.invalid")

        MalaiSchema.resetToMigratedState()

        assertThat(countUsersMatching("left-behind-%")).isZero()
        assertThat(registeredLedgerAccounts()).hasSize(3)
    }

    @Test
    fun `reset refuses a database that is not the test database`() {
        val somebodyElsesDatabase = url.replace("malai_test", "malai_production_like")

        val thrown = assertThrows<IllegalArgumentException> {
            MalaiSchema.resetToMigratedState(somebodyElsesDatabase, user, password)
        }

        assertThat(thrown).hasMessageContaining("malai_production_like")
    }

    private fun registeredLedgerAccounts(): List<String> =
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.createStatement().executeQuery("SELECT account_id FROM ledger_accounts").use { rows ->
                generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
            }
        }

    private fun insertUser(email: String) {
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO users (id, email, first_name, last_name, kyc_status, registration_confirmed, created_at, created_by)
                VALUES (?, ?, 'Left', 'Behind', 'VERIFIED', TRUE, now(), 'malai-schema-test')
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, UUID.randomUUID())
                statement.setString(2, email)
                statement.executeUpdate()
            }
        }
    }

    private fun countUsersMatching(pattern: String): Int =
        DriverManager.getConnection(url, user, password).use { connection ->
            connection.prepareStatement("SELECT count(*) FROM users WHERE email LIKE ?").use { statement ->
                statement.setString(1, pattern)
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt(1)
                }
            }
        }
}
