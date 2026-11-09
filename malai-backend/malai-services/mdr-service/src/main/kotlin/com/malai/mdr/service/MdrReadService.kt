package com.malai.mdr.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.malai.commons.exception.MalaiCode.MDR_DECISION_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import com.malai.mdr.exception.MdrOutputNotActionableException
import com.malai.mdr.model.MdrActionableOutputResponse
import com.malai.mdr.model.MdrDecisionSummariesResponse
import com.malai.mdr.model.MdrDecisionRecordResponse
import com.malai.mdr.model.MdrDecisionSummaryView
import com.malai.mdr.model.MdrDecisionThreadResponse
import com.malai.mdr.model.MdrDispositionView
import com.malai.mdr.model.MdrModelDecisionView
import com.malai.mdr.model.MdrRetrievedContextRefView
import com.malai.mdr.model.MdrRulingState
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

// The MDR read surface, where the "no model output is actionable without a qualifying Human
// Disposition" invariant is enforced -- the only enforcement site until real consumers exist.
//
// Both reads load the same Entry thread: every row sharing the Decision's decision_id, seq-ordered
// (the Model Decision is always the earliest, appended before any ruling could exist). The replay
// read serves the whole thread and derives the EDIT diff at read time; the gate serves the Ruling
// in force -- latest Disposition wins, so a mistaken accept is corrected by a later reject and a
// reject is re-opened by a later accept, with the full ruling history intact in the replay read.
@Service
class MdrReadService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun listDecisions(rulingState: MdrRulingState?, advisory: Boolean?): MdrDecisionSummariesResponse =
        MdrDecisionSummariesResponse(
            jdbc.queryForList(LIST_DECISION_SUMMARIES)
                .map { it.toSummaryView() }
                .filter { rulingState.admits(it.rulingInForce) }
                .filter { advisory == null || advisory == it.advisory },
        )

    fun replay(decisionId: UUID): MdrDecisionThreadResponse {
        val thread = loadThread(decisionId)
        return MdrDecisionThreadResponse(
            decisionId = decisionId,
            correlationType = thread.correlationType,
            correlationId = thread.correlationId,
            modelDecision = thread.modelDecision.toView(),
            dispositions = thread.dispositions.map { it.toView(thread.modelDecision.rawOutput) },
            rulingInForce = thread.dispositions.lastOrNull()?.disposition,
        )
    }

    fun decisionRecord(decisionId: UUID): MdrDecisionRecordResponse {
        val thread = loadThread(decisionId)
        val decision = thread.modelDecision
        return MdrDecisionRecordResponse(
            decisionId = decisionId,
            entryId = decision.entryId,
            seq = decision.seq,
            ts = decision.ts.toString(),
            outcome = decision.outcome,
            advisory = decision.advisory,
            correlationType = thread.correlationType,
            correlationId = thread.correlationId,
            modelId = decision.modelId,
            modelVersion = decision.modelVersion,
            verdict = decision.verdict,
            retrievedContext = decision.retrievedContext?.map { it.toRefView() },
            errorDetail = decision.errorDetail,
            rulingInForce = thread.dispositions.lastOrNull()?.disposition,
        )
    }

    fun actionableOutput(decisionId: UUID): MdrActionableOutputResponse {
        val thread = loadThread(decisionId)
        val ruling = thread.dispositions.lastOrNull() ?: throw notActionable()
        return when (ruling.disposition) {
            "ACCEPT" -> MdrActionableOutputResponse(
                decisionId = decisionId,
                ruling = "ACCEPT",
                rulingEntryId = ruling.entryId,
                actionableOutput = thread.modelDecision.rawOutput ?: throw notActionable(),
            )
            "EDIT" -> MdrActionableOutputResponse(
                decisionId = decisionId,
                ruling = "EDIT",
                rulingEntryId = ruling.entryId,
                actionableOutput = ruling.editedOutput!!,
            )
            else -> throw notActionable()
        }
    }

    private fun loadThread(decisionId: UUID): ThreadData {
        val rows = jdbc.queryForList("$SELECT_COLUMNS WHERE decision_id = ? ORDER BY seq ASC", decisionId)
        if (rows.isEmpty()) throw NotFoundException(MDR_DECISION_NOT_FOUND)
        val modelRow = rows.firstOrNull { it["entry_kind"] == "MODEL_DECISION" }
            ?: throw NotFoundException(MDR_DECISION_NOT_FOUND)
        return ThreadData(
            correlationType = modelRow["correlation_type"] as String,
            correlationId = modelRow["correlation_id"] as String,
            modelDecision = modelRow.toModelDecisionData(),
            dispositions = rows.filter { it["entry_kind"] == "HUMAN_DISPOSITION" }.map { it.toDispositionData() },
        )
    }

    private fun notActionable() = MdrOutputNotActionableException()

    private fun MdrRulingState?.admits(rulingInForce: String?): Boolean = when (this) {
        null -> true
        MdrRulingState.AWAITING_RULING -> rulingInForce == null
        else -> rulingInForce == name
    }

    private fun Map<String, Any?>.toSummaryView() = MdrDecisionSummaryView(
        decisionId = this["decision_id"] as UUID,
        entryId = this["entry_id"] as UUID,
        seq = (this["seq"] as Number).toLong(),
        ts = (this["ts"] as Timestamp).toInstant().toString(),
        outcome = this["outcome"] as String,
        modelId = this["model_id"] as String,
        modelVersion = this["model_version"] as String,
        correlationType = this["correlation_type"] as String,
        correlationId = this["correlation_id"] as String,
        advisory = this["advisory"] as Boolean? ?: false,
        dispositionCount = (this["disposition_count"] as Number).toLong(),
        latencyMs = (this["latency_ms"] as Number).toLong(),
        costMicros = (this["cost_micros"] as Number).toLong(),
        costCurrency = this["cost_currency"] as String,
        rulingInForce = this["ruling_in_force"] as String?,
        verdict = (this["verdict"] as String?)?.let { objectMapper.readValue(it, MAP_TYPE) },
    )

    private fun Map<String, Any?>.toModelDecisionData() = ModelDecisionData(
        entryId = this["entry_id"] as UUID,
        seq = (this["seq"] as Number).toLong(),
        ts = (this["ts"] as Timestamp).toInstant(),
        outcome = this["outcome"] as String,
        modelId = this["model_id"] as String,
        modelVersion = this["model_version"] as String,
        advisory = this["advisory"] as Boolean? ?: false,
        promptTemplateId = this["prompt_template_id"] as String,
        promptTemplateVersion = this["prompt_template_version"] as String,
        promptTemplateHashHex = toHex(this["prompt_template_hash"] as ByteArray),
        renderedPrompt = this["rendered_prompt"] as String,
        retrievedContext = (this["retrieved_context"] as String?)?.let { objectMapper.readValue(it, LIST_TYPE) },
        rawOutput = this["raw_output"] as String?,
        verdict = (this["verdict"] as String?)?.let { objectMapper.readValue(it, MAP_TYPE) },
        errorDetail = this["error_detail"] as String?,
        latencyMs = (this["latency_ms"] as Number).toLong(),
        costMicros = (this["cost_micros"] as Number).toLong(),
        costCurrency = this["cost_currency"] as String,
    )

    private fun Map<String, Any?>.toDispositionData() = DispositionData(
        entryId = this["entry_id"] as UUID,
        seq = (this["seq"] as Number).toLong(),
        ts = (this["ts"] as Timestamp).toInstant(),
        disposition = this["disposition"] as String,
        humanId = this["human_id"] as UUID,
        parentEntryId = this["parent_entry_id"] as UUID,
        editedOutput = this["edited_output"] as String?,
    )

    private fun ModelDecisionData.toView() = MdrModelDecisionView(
        entryId = entryId,
        seq = seq,
        ts = ts.toString(),
        outcome = outcome,
        modelId = modelId,
        modelVersion = modelVersion,
        advisory = advisory,
        promptTemplateId = promptTemplateId,
        promptTemplateVersion = promptTemplateVersion,
        promptTemplateHash = promptTemplateHashHex,
        renderedPrompt = renderedPrompt,
        retrievedContext = retrievedContext?.map { it.toRefView() },
        rawOutput = rawOutput,
        verdict = verdict,
        errorDetail = errorDetail,
        latencyMs = latencyMs,
        costMicros = costMicros,
        costCurrency = costCurrency,
    )

    private fun Map<String, Any?>.toRefView() = MdrRetrievedContextRefView(
        type = this["type"] as String,
        id = this["id"] as String,
        contentHash = this["contentHash"] as String,
    )

    private fun DispositionData.toView(rawOutput: String?) = MdrDispositionView(
        entryId = entryId,
        seq = seq,
        ts = ts.toString(),
        disposition = disposition,
        humanId = humanId,
        parentEntryId = parentEntryId,
        editedOutput = editedOutput,
        diff = editedOutput?.let { OutputDiff.between(rawOutput ?: "", it) },
    )

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private data class ThreadData(
        val correlationType: String,
        val correlationId: String,
        val modelDecision: ModelDecisionData,
        val dispositions: List<DispositionData>,
    )

    private data class ModelDecisionData(
        val entryId: UUID,
        val seq: Long,
        val ts: Instant,
        val outcome: String,
        val modelId: String,
        val modelVersion: String,
        val advisory: Boolean,
        val promptTemplateId: String,
        val promptTemplateVersion: String,
        val promptTemplateHashHex: String,
        val renderedPrompt: String,
        val retrievedContext: List<Map<String, Any?>>?,
        val rawOutput: String?,
        val verdict: Map<String, Any?>?,
        val errorDetail: String?,
        val latencyMs: Long,
        val costMicros: Long,
        val costCurrency: String,
    )

    private data class DispositionData(
        val entryId: UUID,
        val seq: Long,
        val ts: Instant,
        val disposition: String,
        val humanId: UUID,
        val parentEntryId: UUID,
        val editedOutput: String?,
    )

    companion object {
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val LIST_TYPE = object : TypeReference<List<Map<String, Any?>>>() {}

        private const val LIST_DECISION_SUMMARIES =
            """
            SELECT d.decision_id, d.entry_id, d.seq, d.ts, d.outcome, d.model_id, d.model_version,
                   d.correlation_type, d.correlation_id, d.advisory,
                   d.latency_ms, d.cost_micros, d.cost_currency, d.verdict::text AS verdict,
                   COALESCE(r.disposition_count, 0) AS disposition_count,
                   r.ruling_in_force
            FROM mdr_entries d
            LEFT JOIN (
                SELECT decision_id,
                       COUNT(*) AS disposition_count,
                       (ARRAY_AGG(disposition ORDER BY seq DESC))[1] AS ruling_in_force
                FROM mdr_entries
                WHERE entry_kind = 'HUMAN_DISPOSITION'
                GROUP BY decision_id
            ) r ON r.decision_id = d.decision_id
            WHERE d.entry_kind = 'MODEL_DECISION'
            ORDER BY d.seq DESC
            """

        private const val SELECT_COLUMNS =
            """
            SELECT entry_id, seq, ts, entry_kind, decision_id, correlation_type, correlation_id,
                   model_id, model_version, advisory, prompt_template_id, prompt_template_version,
                   prompt_template_hash, rendered_prompt, retrieved_context::text AS retrieved_context,
                   outcome, raw_output, verdict::text AS verdict, error_detail, latency_ms,
                   cost_micros, cost_currency, parent_entry_id, human_id, disposition, edited_output
            FROM mdr_entries
            """
    }
}
