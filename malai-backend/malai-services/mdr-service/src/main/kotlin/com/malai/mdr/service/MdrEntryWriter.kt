package com.malai.mdr.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.malai.commons.TimeService
import com.malai.commons.model.Currency
import com.malai.mdr.chain.MdrEntryCanonicalJson
import com.malai.mdr.chain.MdrEntryFields
import com.malai.security.audit.AuditRedactor
import com.malai.security.chain.ChainAppender
import com.malai.security.chain.ChainHeads
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class RecordedModelDecision(
    val entryId: UUID,
    val decisionId: UUID,
    val correlationType: String,
    val correlationId: String,
    val advisory: Boolean,
    val modelId: String,
    val modelVersion: String,
    val promptTemplateId: String,
    val promptTemplateVersion: String,
    val promptTemplateHash: ByteArray,
    val renderedPrompt: String,
    val retrievedContext: List<Map<String, Any?>>?,
    val outcome: String,
    val rawOutput: String?,
    val verdict: Map<String, Any?>?,
    val errorDetail: String?,
    val latencyMs: Long,
    val costMicros: Long,
    val costCurrency: Currency,
)

data class RecordedHumanDisposition(
    val entryId: UUID,
    val decisionId: UUID,
    val parentEntryId: UUID,
    val correlationType: String,
    val correlationId: String,
    val humanId: UUID,
    val disposition: String,
    val editedOutput: String?,
)

data class AppendedMdrEntry(val entryId: UUID, val seq: Long)

// Appends a Model Decision Entry to the MDR hash chain (scope 'mdr'), the second tenant of the
// shared chain core beside the audit plane's 'global' scope. Mirrors [HashChainedAuditWriter]:
// the [ChainAppender] locks the scope's chain_head row (SELECT ... FOR UPDATE), hashes
// SHA-256(prev_hash || canonical payload), inserts the row, and advances the head -- all inside
// the caller's @Transactional, so the Entry commits with the surrounding write.
//
// Rendered prompt, Raw output, retrieved-context refs and verdict pass through the platform
// [AuditRedactor] BEFORE they are hashed and persisted, exactly as the audit chain redacts its
// state maps -- the chain hashes the redacted form, so a leaked secret can never be recovered
// from the record. The JSONB-bound values (retrieved_context, verdict) are round-tripped through
// Jackson before hashing so the writer hashes the same form the verifier reads back from JSONB.
@Service
class MdrEntryWriter(
    private val jdbc: JdbcTemplate,
    private val timeService: TimeService,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val chainAppender = ChainAppender(ChainHeads(jdbc))

    @Transactional(propagation = Propagation.REQUIRED)
    fun record(decision: RecordedModelDecision): AppendedMdrEntry = appendChained(decision)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun recordIndependently(decision: RecordedModelDecision): AppendedMdrEntry = appendChained(decision)

    // Appends a Human Disposition Entry to the same 'mdr' chain, after the Model Decision it rules
    // on. Because the chain is append-only and the decision was appended first, the disposition's
    // hash transitively covers the decision -- "this human saw exactly that output" is proven by
    // the chain itself. The parent link, the decision's correlation and the ruling human's id are
    // carried; the model-side columns stay null (the DB CHECK enforces the shape). The edited
    // output -- the actionable artifact for an EDIT ruling -- is redacted BEFORE it is hashed and
    // persisted, exactly as raw output is on a decision.
    @Transactional(propagation = Propagation.REQUIRED)
    fun recordDisposition(disposition: RecordedHumanDisposition): AppendedMdrEntry {
        val editedOutput = disposition.editedOutput?.let(::redactText)

        var stampedTs: Instant? = null

        val link = chainAppender.append(
            SCOPE,
            {
                val ts = timeService.now().truncatedTo(ChronoUnit.MICROS)
                stampedTs = ts
                MdrEntryCanonicalJson.serialize(
                    MdrEntryFields(
                        entryKind = HUMAN_DISPOSITION,
                        ts = ts,
                        decisionId = disposition.decisionId,
                        parentEntryId = disposition.parentEntryId,
                        correlationType = disposition.correlationType,
                        correlationId = disposition.correlationId,
                        advisory = null,
                        modelId = null,
                        modelVersion = null,
                        promptTemplateId = null,
                        promptTemplateVersion = null,
                        promptTemplateHashHex = null,
                        renderedPrompt = null,
                        retrievedContext = null,
                        outcome = null,
                        rawOutput = null,
                        verdict = null,
                        errorDetail = null,
                        latencyMs = null,
                        costMicros = null,
                        costCurrency = null,
                        humanId = disposition.humanId,
                        disposition = disposition.disposition,
                        editedOutput = editedOutput,
                    ),
                )
            },
        ) { link ->
            jdbc.update(
                """
                INSERT INTO mdr_entries
                    (entry_id, ts, entry_kind, decision_id, parent_entry_id,
                     correlation_type, correlation_id, advisory, model_id, model_version,
                     prompt_template_id, prompt_template_version, prompt_template_hash,
                     rendered_prompt, retrieved_context, outcome, raw_output, verdict,
                     error_detail, latency_ms, cost_micros, cost_currency,
                     human_id, disposition, edited_output,
                     prev_hash, current_hash, seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                disposition.entryId,
                Timestamp.from(stampedTs),
                HUMAN_DISPOSITION,
                disposition.decisionId,
                disposition.parentEntryId,
                disposition.correlationType,
                disposition.correlationId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                disposition.humanId,
                disposition.disposition,
                editedOutput,
                link.prevHash,
                link.currentHash,
                link.seq,
            )
        }

        log.debug(
            "mdr chain advanced seq={} decisionId={} disposition={}",
            link.seq, disposition.decisionId, disposition.disposition,
        )
        return AppendedMdrEntry(entryId = disposition.entryId, seq = link.seq)
    }

    private fun appendChained(decision: RecordedModelDecision): AppendedMdrEntry {
        val renderedPrompt = redactText(decision.renderedPrompt)
        val rawOutput = decision.rawOutput?.let(::redactText)
        val retrievedContext = roundTripList(redactList(decision.retrievedContext))
        val verdict = roundTripMap(AuditRedactor.redact(decision.verdict))
        val promptTemplateHashHex = toHex(decision.promptTemplateHash)

        var stampedTs: Instant? = null

        val link = chainAppender.append(
            SCOPE,
            {
                val ts = timeService.now().truncatedTo(ChronoUnit.MICROS)
                stampedTs = ts
                MdrEntryCanonicalJson.serialize(
                    MdrEntryFields(
                        entryKind = MODEL_DECISION,
                        ts = ts,
                        decisionId = decision.decisionId,
                        parentEntryId = null,
                        correlationType = decision.correlationType,
                        correlationId = decision.correlationId,
                        advisory = decision.advisory,
                        modelId = decision.modelId,
                        modelVersion = decision.modelVersion,
                        promptTemplateId = decision.promptTemplateId,
                        promptTemplateVersion = decision.promptTemplateVersion,
                        promptTemplateHashHex = promptTemplateHashHex,
                        renderedPrompt = renderedPrompt,
                        retrievedContext = retrievedContext,
                        outcome = decision.outcome,
                        rawOutput = rawOutput,
                        verdict = verdict,
                        errorDetail = decision.errorDetail,
                        latencyMs = decision.latencyMs,
                        costMicros = decision.costMicros,
                        costCurrency = decision.costCurrency.name,
                        humanId = null,
                        disposition = null,
                        editedOutput = null,
                    ),
                )
            },
        ) { link ->
            jdbc.update(
                """
                INSERT INTO mdr_entries
                    (entry_id, ts, entry_kind, decision_id, parent_entry_id,
                     correlation_type, correlation_id, advisory, model_id, model_version,
                     prompt_template_id, prompt_template_version, prompt_template_hash,
                     rendered_prompt, retrieved_context, outcome, raw_output, verdict,
                     error_detail, latency_ms, cost_micros, cost_currency,
                     human_id, disposition, edited_output,
                     prev_hash, current_hash, seq)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?::jsonb,
                        ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                decision.entryId,
                Timestamp.from(stampedTs),
                MODEL_DECISION,
                decision.decisionId,
                null,
                decision.correlationType,
                decision.correlationId,
                decision.advisory,
                decision.modelId,
                decision.modelVersion,
                decision.promptTemplateId,
                decision.promptTemplateVersion,
                decision.promptTemplateHash,
                renderedPrompt,
                retrievedContext?.let(::toJson),
                decision.outcome,
                rawOutput,
                verdict?.let(::toJson),
                decision.errorDetail,
                decision.latencyMs,
                decision.costMicros,
                decision.costCurrency.name,
                null,
                null,
                null,
                link.prevHash,
                link.currentHash,
                link.seq,
            )
        }

        log.debug("mdr chain advanced seq={} decisionId={} outcome={}", link.seq, decision.decisionId, decision.outcome)
        return AppendedMdrEntry(entryId = decision.entryId, seq = link.seq)
    }

    private fun redactText(value: String): String =
        AuditRedactor.redact(mapOf(REDACT_KEY to value))!![REDACT_KEY] as String

    private fun redactList(list: List<Map<String, Any?>>?): List<Map<String, Any?>>? =
        list?.map { AuditRedactor.redact(it)!! }

    private fun roundTripMap(map: Map<String, Any?>?): Map<String, Any?>? =
        map?.let { objectMapper.readValue(objectMapper.writeValueAsString(it), MAP_TYPE) }

    private fun roundTripList(list: List<Map<String, Any?>>?): List<Map<String, Any?>>? =
        list?.let { objectMapper.readValue(objectMapper.writeValueAsString(it), LIST_TYPE) }

    private fun toJson(value: Any): String = objectMapper.writeValueAsString(value)

    private fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    companion object {
        private const val SCOPE = "mdr"
        private const val MODEL_DECISION = "MODEL_DECISION"
        private const val HUMAN_DISPOSITION = "HUMAN_DISPOSITION"
        private const val REDACT_KEY = "value"
        private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
        private val LIST_TYPE = object : TypeReference<List<Map<String, Any?>>>() {}
    }
}
