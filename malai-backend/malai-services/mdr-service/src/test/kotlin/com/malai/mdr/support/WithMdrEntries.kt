package com.malai.mdr.support

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.malai.commons.model.Currency
import com.malai.mdr.chain.MdrEntryCanonicalJson
import com.malai.mdr.chain.MdrEntryRowMapper
import com.malai.mdr.service.MdrEntryWriter
import com.malai.mdr.service.RecordedHumanDisposition
import com.malai.mdr.service.RecordedModelDecision
import com.malai.security.chain.ChainHasher
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Domain fixtures for the MDR service: reset the `mdr` chain to genesis, and read persisted
 * entries + chain head back for assertions. [theMdrEntryInTheDatabaseIs] yields the full row as a
 * typed [PersistedMdrEntry] (every content column) so a new column forces the test to update.
 * [recomputedCurrentHashHex] re-derives SHA-256(prev_hash || canonical payload) from a stored row
 * via the SAME projection the writer used, so a test can prove the chain link is genuine.
 */
interface WithMdrEntries : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun theMdrChainIsAtGenesis() = CallableAction {
        jdbc().update("DELETE FROM mdr_entries")
        jdbc().update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES ('mdr', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
        )
    }

    fun anMdrModelDecisionIsRecorded(
        entryId: UUID,
        decisionId: UUID,
        rawOutput: String? = "APPROVE: low risk.",
        outcome: String = "COMPLETED",
        errorDetail: String? = null,
        correlationType: String = "PAYMENT",
        correlationId: String = "pmt-verify",
        retrievedContext: List<Map<String, Any?>>? = null,
        advisory: Boolean = false,
        verdict: Map<String, Any?>? = null,
    ) = CallableAction {
        getBean(MdrEntryWriter::class.java).record(
            RecordedModelDecision(
                entryId = entryId,
                decisionId = decisionId,
                correlationType = correlationType,
                correlationId = correlationId,
                advisory = advisory,
                modelId = "claude-opus-4-8",
                modelVersion = "2026-01",
                promptTemplateId = "fraud-triage",
                promptTemplateVersion = "v3",
                promptTemplateHash = ByteArray(32) { 0x01 },
                renderedPrompt = "Assess payment for fraud risk.",
                retrievedContext = retrievedContext,
                outcome = outcome,
                rawOutput = rawOutput,
                verdict = verdict,
                errorDetail = errorDetail,
                latencyMs = 10L,
                costMicros = 5L,
                costCurrency = Currency.GBP,
            ),
        )
    }

    fun anMdrHumanDispositionIsRecorded(
        entryId: UUID,
        decisionId: UUID,
        parentEntryId: UUID,
        disposition: String,
        editedOutput: String? = null,
    ) = CallableAction {
        getBean(MdrEntryWriter::class.java).recordDisposition(
            RecordedHumanDisposition(
                entryId = entryId,
                decisionId = decisionId,
                parentEntryId = parentEntryId,
                correlationType = "PAYMENT",
                correlationId = "pmt-verify",
                humanId = UUID.fromString("ad000000-0000-0000-0000-0000000000aa"),
                disposition = disposition,
                editedOutput = editedOutput,
            ),
        )
    }

    fun theMdrChainHeadInTheDatabase() = StateCollector { _ ->
        jdbc().queryForMap("SELECT scope, last_hash, last_seq FROM chain_head WHERE scope = 'mdr'")
    }

    fun theMdrEntriesInTheDatabase() = StateCollector { _ ->
        jdbc().queryForList("$SELECT_ENTRY_COLUMNS ORDER BY seq ASC")
    }

    fun theMdrEntryInTheDatabaseIs(entryId: UUID) = StateCollector<PersistedMdrEntry> { _ ->
        val rows = jdbc().queryForList("$SELECT_ENTRY_COLUMNS WHERE entry_id = ?", entryId)
        val row = rows.firstOrNull() ?: throw AssertionError("MDR entry $entryId not found in database")
        toPersistedMdrEntry(row)
    }

    fun recomputedCurrentHashHex(row: Map<String, Any?>): String =
        toHex(ChainHasher.hash(
            row["prev_hash"] as ByteArray,
            MdrEntryCanonicalJson.serialize(MdrEntryRowMapper.toFields(row, OBJECT_MAPPER)),
        ))

    fun theCurrentHashOfMdrEntryAtSeqIsTamperedTo(seq: Long, badHash: ByteArray) = CallableAction {
        jdbc().update("UPDATE mdr_entries SET current_hash = ? WHERE seq = ?", badHash, seq)
    }

    fun theAdvisoryFlagOfMdrEntryAtSeqIsTamperedTo(seq: Long, advisory: Boolean) = CallableAction {
        jdbc().update("UPDATE mdr_entries SET advisory = ? WHERE seq = ?", advisory, seq)
    }

    fun theMdrEntryAtSeqPredatesTheAdvisoryColumn(seq: Long) = CallableAction {
        jdbc().update("UPDATE mdr_entries SET advisory = NULL WHERE seq = ?", seq)
    }

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun toPersistedMdrEntry(row: Map<String, Any?>) = PersistedMdrEntry(
        entryId = row["entry_id"] as UUID,
        ts = (row["ts"] as Timestamp).toInstant(),
        entryKind = row["entry_kind"] as String,
        decisionId = row["decision_id"] as UUID,
        parentEntryId = row["parent_entry_id"] as UUID?,
        correlationType = row["correlation_type"] as String,
        correlationId = row["correlation_id"] as String,
        advisory = row["advisory"] as Boolean?,
        modelId = row["model_id"] as String?,
        modelVersion = row["model_version"] as String?,
        promptTemplateId = row["prompt_template_id"] as String?,
        promptTemplateVersion = row["prompt_template_version"] as String?,
        promptTemplateHashHex = (row["prompt_template_hash"] as ByteArray?)?.let(::toHex),
        renderedPrompt = row["rendered_prompt"] as String?,
        retrievedContext = (row["retrieved_context"] as String?)?.let(::parseList),
        outcome = row["outcome"] as String?,
        rawOutput = row["raw_output"] as String?,
        verdict = (row["verdict"] as String?)?.let(::parseMap),
        errorDetail = row["error_detail"] as String?,
        latencyMs = (row["latency_ms"] as Number?)?.toLong(),
        costMicros = (row["cost_micros"] as Number?)?.toLong(),
        costCurrency = row["cost_currency"] as String?,
        humanId = row["human_id"] as UUID?,
        disposition = row["disposition"] as String?,
        editedOutput = row["edited_output"] as String?,
        seq = (row["seq"] as Number).toLong(),
    )

    private fun parseMap(json: String): Map<String, Any?> = OBJECT_MAPPER.readValue(json, MAP_TYPE)
    private fun parseList(json: String): List<Map<String, Any?>> = OBJECT_MAPPER.readValue(json, LIST_TYPE)

    companion object {
        private val OBJECT_MAPPER = ObjectMapper()
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val LIST_TYPE = object : TypeReference<List<Map<String, Any?>>>() {}

        private const val SELECT_ENTRY_COLUMNS =
            """
            SELECT seq, prev_hash, current_hash, entry_id, ts, entry_kind, decision_id, parent_entry_id,
                   correlation_type, correlation_id, advisory, model_id, model_version, prompt_template_id,
                   prompt_template_version, prompt_template_hash, rendered_prompt,
                   retrieved_context::text AS retrieved_context, outcome, raw_output,
                   verdict::text AS verdict, error_detail, latency_ms, cost_micros, cost_currency,
                   human_id, disposition, edited_output
            FROM mdr_entries
            """
    }
}

data class PersistedMdrEntry(
    val entryId: UUID,
    val ts: Instant,
    val entryKind: String,
    val decisionId: UUID,
    val parentEntryId: UUID?,
    val correlationType: String,
    val correlationId: String,
    val advisory: Boolean?,
    val modelId: String?,
    val modelVersion: String?,
    val promptTemplateId: String?,
    val promptTemplateVersion: String?,
    val promptTemplateHashHex: String?,
    val renderedPrompt: String?,
    val retrievedContext: List<Map<String, Any?>>?,
    val outcome: String?,
    val rawOutput: String?,
    val verdict: Map<String, Any?>?,
    val errorDetail: String?,
    val latencyMs: Long?,
    val costMicros: Long?,
    val costCurrency: String?,
    val humanId: UUID?,
    val disposition: String?,
    val editedOutput: String?,
    val seq: Long,
)
