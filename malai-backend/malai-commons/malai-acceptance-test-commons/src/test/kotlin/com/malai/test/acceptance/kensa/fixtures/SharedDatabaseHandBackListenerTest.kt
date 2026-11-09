package com.malai.test.acceptance.kensa.fixtures

import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.platform.launcher.TestExecutionListener
import org.junit.platform.launcher.TestPlan
import org.mockito.Mockito.mock
import java.util.ServiceLoader

class SharedDatabaseHandBackListenerTest {

    @Test
    fun `is discovered by the junit platform so no module has to opt in`() {
        val discovered = ServiceLoader.load(TestExecutionListener::class.java).toList()

        assertThat(discovered).hasAtLeastOneElementOfType(SharedDatabaseHandBackListener::class.java)
    }

    @Test
    fun `hands the database back once the test plan has finished`() {
        MalaiAcceptanceTest.cleanTables()

        SharedDatabaseHandBackListener().testPlanExecutionFinished(mock(TestPlan::class.java))

        assertThat(registeredLedgerAccounts()).hasSize(3)
    }

    private fun registeredLedgerAccounts(): List<String> =
        DatabaseConnection.createConnection(MalaiAcceptanceTestContext.DATABASE_URL).use { connection ->
            connection.createStatement().executeQuery("SELECT account_id FROM ledger_accounts").use { rows ->
                generateSequence { if (rows.next()) rows.getString(1) else null }.toList()
            }
        }
}
