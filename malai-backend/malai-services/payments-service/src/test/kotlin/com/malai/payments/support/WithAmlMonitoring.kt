package com.malai.payments.support

import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Plants — and lifts — AML monitoring on a payer account, over raw SQL.
 *
 * payments-service has no dependency on aml-service and must never acquire one (the
 * `onlyTheAggregatorDependsOn` architecture rule holds that line), so the tipping-off tests
 * cannot ask aml-service to open a case. They write the row the sweep would have written and
 * then prove the customer surface cannot tell.
 */
interface WithAmlMonitoring : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun theAccountIsUnderAmlMonitoring(
        accountId: UUID,
        caseId: UUID,
        alertDecisionId: UUID,
        openedAt: String = "2026-10-15T10:00:00Z",
    ) = CallableAction {
        jdbc().execute(ConnectionCallback { connection: Connection ->
            connection.prepareStatement(INSERT_OPEN_CASE).use { statement ->
                statement.setObject(1, caseId)
                statement.setObject(2, accountId)
                statement.setArray(3, connection.createArrayOf("uuid", arrayOf(alertDecisionId)))
                statement.setTimestamp(4, Timestamp.from(Instant.parse(openedAt)))
                statement.setTimestamp(5, Timestamp.from(Instant.parse(openedAt)))
                statement.executeUpdate()
            }
        })
    }

    fun theAccountIsNoLongerUnderAmlMonitoring() = CallableAction {
        jdbc().update("DELETE FROM aml_cases")
    }

    fun theAmlCasesForAccountInTheDatabase(accountId: UUID) = StateCollector { _ ->
        jdbc().queryForList(
            "SELECT case_id, status FROM aml_cases WHERE account_id = ?",
            accountId,
        ).map { MonitoredAccountCase(it["case_id"] as UUID, it["status"] as String) }
    }

    companion object {
        private const val INSERT_OPEN_CASE =
            """INSERT INTO aml_cases (case_id, account_id, status, alert_decision_ids, opened_at, updated_at)
               VALUES (?, ?, 'OPEN', ?, ?, ?)"""
    }
}

data class MonitoredAccountCase(
    val caseId: UUID,
    val status: String,
)
