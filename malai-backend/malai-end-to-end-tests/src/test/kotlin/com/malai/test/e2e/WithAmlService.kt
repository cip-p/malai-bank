package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import java.util.UUID

/**
 * E2E helpers for the AML investigation surface bundled into malai-api:
 *
 *  - `/api/v1/admin/aml/cases` — the ADMIN worklist of cases the monitoring sweep opened.
 *  - `/api/v1/admin/aml/cases/{caseId}` — the case detail: each Alert read back from the MDR with
 *    its ruling in force, each evidence payment read back from payments-service.
 *  - `/api/v1/admin/aml/cases/{caseId}/sar-draft` — records a grounded SAR draft as its own Model
 *    Decision and serves the narrative.
 *  - `/api/v1/admin/aml/cases/{caseId}/close-reported` — asks the MDR Gate whether the SAR draft is
 *    actionable and files what comes back.
 *
 * Detection itself has no endpoint. The sweep is a scheduled job inside malai-api, so a test waits
 * for the alert to land with [anAmlCaseForAccountAppears] — a JDBC poll deliberately kept off the
 * sequence diagram, since the officer's first *observation* of the case is the worklist call.
 */
interface WithAmlService : WithCognitoJwtToken {

    val amlServiceHttpClient: HttpClientStub

    fun amlServiceListsCases(adminUserId: UUID, status: String? = null): Action<ActionContext> =
        amlServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/aml/cases")
                .apply { status?.let { queryParam("status", it) } }
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun amlServiceReadsCase(adminUserId: UUID, caseId: UUID): Action<ActionContext> =
        amlServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/aml/cases/$caseId")
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun amlServiceDraftsTheSar(adminUserId: UUID, caseId: UUID, idempotencyKey: String): Action<ActionContext> =
        amlServiceHttpClient.sends(
            post()
                .url("/api/v1/admin/aml/cases/$caseId/sar-draft")
                .header("Idempotency-Key", idempotencyKey)
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun amlServiceClosesCaseAsReported(
        adminUserId: UUID,
        caseId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        amlServiceHttpClient.sends(
            post()
                .url("/api/v1/admin/aml/cases/$caseId/close-reported")
                .header("Idempotency-Key", idempotencyKey)
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    /**
     * Block until the monitoring sweep has opened a case on [accountId]. The sweep's cadence
     * (`malai.aml.sweep.cron`, every ten seconds by default) is a latency budget, not a contract —
     * poll rather than sleep, exactly as the referred-payment tests do for their resolver.
     */
    fun anAmlCaseForAccountAppears(
        accountId: UUID,
        timeoutMs: Long = 45_000L,
        pollIntervalMs: Long = 500L,
    ) = CallableAction {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (readCasesForAccount(accountId).isNotEmpty()) return@CallableAction
            Thread.sleep(pollIntervalMs)
        }
        error("No AML case for account $accountId appeared within ${timeoutMs}ms")
    }

    fun theAmlCasesForAccountInTheDatabase(accountId: UUID) = StateCollector { _ ->
        readCasesForAccount(accountId)
    }

    private fun readCasesForAccount(accountId: UUID): List<AmlCaseInDb> =
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.prepareStatement(
                """
                SELECT case_id, account_id, status, alert_decision_ids, sar_decision_id, sar_narrative
                FROM aml_cases
                WHERE account_id = ?
                ORDER BY opened_at ASC
                """.trimIndent(),
            ).use { ps ->
                ps.setObject(1, accountId)
                val rs = ps.executeQuery()
                val out = mutableListOf<AmlCaseInDb>()
                while (rs.next()) {
                    out += AmlCaseInDb(
                        caseId = UUID.fromString(rs.getString("case_id")),
                        accountId = UUID.fromString(rs.getString("account_id")),
                        status = rs.getString("status"),
                        alertDecisionIds = ((rs.getArray("alert_decision_ids").array as Array<*>))
                            .map { UUID.fromString(it.toString()) },
                        sarDecisionId = rs.getString("sar_decision_id")?.let(UUID::fromString),
                        sarNarrative = rs.getString("sar_narrative"),
                    )
                }
                out.toList()
            }
        }
}

data class AmlCaseInDb(
    val caseId: UUID,
    val accountId: UUID,
    val status: String,
    val alertDecisionIds: List<UUID>,
    val sarDecisionId: UUID?,
    val sarNarrative: String?,
)
