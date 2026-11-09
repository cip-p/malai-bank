package com.malai.aml.support

import com.malai.aml.entity.AmlCaseEntity
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.repository.AmlCaseRepository
import com.malai.aml.service.AmlCaseService.Companion.NON_TERMINAL_STATUSES
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.Action
import dev.kensa.GivensContext
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

interface WithAmlCases : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun thereAreNoAmlCasesInTheDatabase() = CallableAction {
        jdbc().update("DELETE FROM aml_cases")
    }

    fun anAmlCaseExists(
        caseId: UUID,
        accountId: UUID,
        status: AmlCaseStatus,
        alertDecisionIds: List<UUID>,
        openedAt: String,
        sarDecisionId: UUID? = null,
    ) = Action<GivensContext> {
        val terminal = status !in NON_TERMINAL_STATUSES
        getBean(AmlCaseRepository::class.java).save(
            AmlCaseEntity(
                caseId = caseId,
                accountId = accountId,
                status = status,
                alertDecisionIds = alertDecisionIds,
                sarDecisionId = sarDecisionId
                    ?: DEFAULT_SAR_DECISION_ID.takeIf { status == AmlCaseStatus.CLOSED_REPORTED },
                sarNarrative = if (status == AmlCaseStatus.CLOSED_REPORTED) "the filed narrative" else null,
                openedAt = Instant.parse(openedAt),
                updatedAt = Instant.parse(openedAt),
                closedAt = if (terminal) Instant.parse(openedAt).plusSeconds(600) else null,
            ),
        )
    }

    fun theAmlCasesInTheDatabase() = StateCollector { _ ->
        jdbc().queryForList("$SELECT_CASE_COLUMNS ORDER BY opened_at ASC").map { toPersistedAmlCase(it) }
    }

    fun theAmlCaseForAccountInTheDatabase(accountId: UUID) = StateCollector<PersistedAmlCase> { _ ->
        val rows = jdbc().queryForList("$SELECT_CASE_COLUMNS WHERE account_id = ?", accountId)
        val row = rows.firstOrNull() ?: throw AssertionError("no AML case for account $accountId in database")
        toPersistedAmlCase(row)
    }

    private fun toPersistedAmlCase(row: Map<String, Any?>): PersistedAmlCase = PersistedAmlCase(
        caseId = row["case_id"] as UUID,
        accountId = row["account_id"] as UUID,
        status = row["status"] as String,
        alertDecisionIds = uuidList(row["alert_decision_ids"]),
        sarDecisionId = row["sar_decision_id"] as UUID?,
        sarNarrative = row["sar_narrative"] as String?,
        openedAt = (row["opened_at"] as Timestamp).toInstant(),
        updatedAt = (row["updated_at"] as Timestamp).toInstant(),
        closedAt = (row["closed_at"] as Timestamp?)?.toInstant(),
    )

    private fun uuidList(column: Any?): List<UUID> =
        ((column as java.sql.Array).array as Array<*>).map { it as UUID }

    companion object {
        private const val SELECT_CASE_COLUMNS =
            """SELECT case_id, account_id, status, alert_decision_ids, sar_decision_id, sar_narrative,
               opened_at, updated_at, closed_at FROM aml_cases"""
        private val DEFAULT_SAR_DECISION_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000005a50")
    }
}

data class PersistedAmlCase(
    val caseId: UUID,
    val accountId: UUID,
    val status: String,
    val alertDecisionIds: List<UUID>,
    val sarDecisionId: UUID?,
    val sarNarrative: String?,
    val openedAt: Instant,
    val updatedAt: Instant,
    val closedAt: Instant?,
)
