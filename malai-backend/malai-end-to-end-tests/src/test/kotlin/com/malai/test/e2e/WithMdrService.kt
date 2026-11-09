package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.WithServiceJwtToken
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.fixtures.DatabaseConnection
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import java.util.UUID

/**
 * E2E helpers for the Model Decision Record surface bundled into malai-api:
 *
 *  - `/api/v1/internal/mdr/decisions` — a service-JWT holder records a Model Decision. The
 *    [mdrServiceRecordsCompletedModelDecision] helper is the e2e stand-in for the test-support
 *    no-op decider: it drives a deterministic COMPLETED Outcome through the internal endpoint with
 *    zero model-provider dependency, so the recorded artifacts are fixed and reproducible.
 *  - `/api/v1/admin/mdr/decisions/{decisionId}/dispositions` — an ADMIN appends a Human Disposition
 *    ruling on the recorded decision.
 *  - `/api/v1/admin/mdr/decisions` — the ADMIN worklist: every Decision with its Ruling in force.
 *  - `/api/v1/admin/mdr/decisions/{decisionId}` — the ADMIN replay read: the recorded prompt,
 *    retrieved-context refs, raw output and verdict, plus every Disposition ruling on it.
 *  - `/api/v1/admin/mdr/chain-verification` — an ADMIN proves the 'mdr' chain intact on demand.
 *
 * The chain reader ([theMdrEntriesInTheDatabase]) reads back the chained rows over raw JDBC (there
 * is no in-JVM Spring context in the e2e run), mirroring [WithAuditEvents].
 */
interface WithMdrService : WithServiceJwtToken, WithCognitoJwtToken {

    val mdrServiceHttpClient: HttpClientStub

    fun mdrServiceRecordsCompletedModelDecision(
        entryId: UUID,
        decisionId: UUID,
        rawOutput: String = "APPROVE: low risk.",
    ): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            post()
                .url("/api/v1/internal/mdr/decisions")
                .contentType("application/json")
                .body(recordModelDecisionBody(entryId, decisionId, rawOutput))
                .withServiceJwtToken()
                .build(),
        )

    fun mdrServiceRecordsAcceptDisposition(
        entryId: UUID,
        decisionId: UUID,
        adminUserId: UUID,
    ): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            post()
                .url("/api/v1/admin/mdr/decisions/$decisionId/dispositions")
                .contentType("application/json")
                .body(recordDispositionBody(entryId, "ACCEPT", editedOutput = null))
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun mdrServiceRecordsEditDisposition(
        entryId: UUID,
        decisionId: UUID,
        adminUserId: UUID,
        editedOutput: String,
    ): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            post()
                .url("/api/v1/admin/mdr/decisions/$decisionId/dispositions")
                .contentType("application/json")
                .body(recordDispositionBody(entryId, "EDIT", editedOutput))
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun mdrServiceListsDecisions(adminUserId: UUID, rulingState: String? = null): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/mdr/decisions")
                .apply { rulingState?.let { queryParam("rulingState", it) } }
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun mdrServiceReplaysDecision(adminUserId: UUID, decisionId: UUID): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/mdr/decisions/$decisionId")
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun mdrServiceVerifiesChain(adminUserId: UUID): Action<ActionContext> =
        mdrServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun theMdrChainIsAtGenesis() = CallableAction {
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            conn.createStatement().execute("DELETE FROM mdr_entries")
            conn.createStatement().execute(
                """
                INSERT INTO chain_head (scope, last_hash, last_seq)
                VALUES ('mdr', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
                ON CONFLICT (scope) DO UPDATE
                    SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
                """.trimIndent(),
            )
        }
    }

    fun theMdrEntriesInTheDatabase() = StateCollector { _ ->
        DatabaseConnection.createConnection(MalaiE2ETestContext.DATABASE_URL).use { conn ->
            val rs = conn.createStatement().executeQuery(
                """
                SELECT seq, entry_id, entry_kind, decision_id, parent_entry_id, outcome, disposition,
                       prev_hash, current_hash
                FROM mdr_entries
                ORDER BY seq ASC
                """.trimIndent(),
            )
            val out = mutableListOf<MdrEntryInDb>()
            while (rs.next()) {
                out += MdrEntryInDb(
                    seq = rs.getLong("seq"),
                    entryId = UUID.fromString(rs.getString("entry_id")),
                    entryKind = rs.getString("entry_kind"),
                    decisionId = UUID.fromString(rs.getString("decision_id")),
                    parentEntryId = rs.getString("parent_entry_id")?.let(UUID::fromString),
                    outcome = rs.getString("outcome"),
                    disposition = rs.getString("disposition"),
                    prevHash = rs.getBytes("prev_hash"),
                    currentHash = rs.getBytes("current_hash"),
                )
            }
            out.toList()
        }
    }

    private fun recordModelDecisionBody(entryId: UUID, decisionId: UUID, rawOutput: String): String =
        testingObjectMapper.writeValueAsString(
            mapOf(
                "entryId" to entryId.toString(),
                "decisionId" to decisionId.toString(),
                "correlationType" to "PAYMENT",
                "correlationId" to "pmt-e2e-1",
                "modelId" to "claude-opus-4-8",
                "modelVersion" to "2026-01",
                "promptTemplateId" to "fraud-triage",
                "promptTemplateVersion" to "v3",
                "promptTemplateHash" to "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
                "renderedPrompt" to "Assess payment pmt-e2e-1 for fraud risk.",
                "retrievedContext" to listOf(mapOf("type" to "CASE", "id" to "case-e2e", "contentHash" to "abc123")),
                "outcome" to "COMPLETED",
                "rawOutput" to rawOutput,
                "verdict" to mapOf("decision" to "APPROVE", "rationale" to "no anomalies"),
                "latencyMs" to 842,
                "costMicros" to 1500,
                "costCurrency" to "GBP",
            ),
        )

    private fun recordDispositionBody(entryId: UUID, disposition: String, editedOutput: String?): String =
        testingObjectMapper.writeValueAsString(
            buildMap {
                put("entryId", entryId.toString())
                put("disposition", disposition)
                if (editedOutput != null) put("editedOutput", editedOutput)
            },
        )
}

data class MdrEntryInDb(
    val seq: Long,
    val entryId: UUID,
    val entryKind: String,
    val decisionId: UUID,
    val parentEntryId: UUID?,
    val outcome: String?,
    val disposition: String?,
    val prevHash: ByteArray,
    val currentHash: ByteArray,
)
