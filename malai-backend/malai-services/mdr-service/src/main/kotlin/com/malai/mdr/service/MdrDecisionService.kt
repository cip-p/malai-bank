package com.malai.mdr.service

import com.malai.mdr.model.RecordModelDecisionRequest
import com.malai.mdr.model.RecordModelDecisionResponse
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Records a Model Decision end-to-end: append the Entry to the MDR chain, then record an
// ordinary audit event on the 'global' scope. Deliberate double-entry, not duplication -- the
// audit chain answers "who did what", the MDR chain answers "what was decided" (plan / ADR-0007).
//
// Idempotency is natural, keyed on the client-supplied entry_id (no Idempotency-Key header, a
// deliberate deviation per the PRD): a replayed request finds the existing Entry and returns it
// unchanged -- no second chain append, no second audit event.
@Service
class MdrDecisionService(
    private val jdbc: JdbcTemplate,
    private val mdrEntryWriter: MdrEntryWriter,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recordDecision(request: RecordModelDecisionRequest): RecordModelDecisionResponse {
        val existing = findExistingEntry(request.entryId!!)
        if (existing != null) {
            log.debug("mdr: entry {} already recorded at seq {} -- returning existing", existing.entryId, existing.seq)
            return existing
        }

        val appended = mdrEntryWriter.record(request.toRecordedModelDecision())

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "MDR_DECISION_RECORDED",
                resourceType = "ModelDecision",
                resourceId = request.decisionId.toString(),
                afterState = mapOf(
                    "entryId" to request.entryId.toString(),
                    "outcome" to request.outcome!!.name,
                ),
            ),
        )

        log.info("mdr: recorded {} decision {} at chain seq {}", request.outcome, request.decisionId, appended.seq)
        return RecordModelDecisionResponse(
            entryId = request.entryId,
            decisionId = request.decisionId!!,
            seq = appended.seq,
            outcome = request.outcome.name,
        )
    }

    private fun findExistingEntry(entryId: UUID): RecordModelDecisionResponse? =
        jdbc.query(
            "SELECT entry_id, decision_id, seq, outcome FROM mdr_entries WHERE entry_id = ?",
            { rs, _ ->
                RecordModelDecisionResponse(
                    entryId = rs.getObject("entry_id", UUID::class.java),
                    decisionId = rs.getObject("decision_id", UUID::class.java),
                    seq = rs.getLong("seq"),
                    outcome = rs.getString("outcome"),
                )
            },
            entryId,
        ).firstOrNull()

    private fun RecordModelDecisionRequest.toRecordedModelDecision() = RecordedModelDecision(
        entryId = entryId!!,
        decisionId = decisionId!!,
        correlationType = correlationType!!,
        correlationId = correlationId!!,
        advisory = advisory,
        modelId = modelId!!,
        modelVersion = modelVersion!!,
        promptTemplateId = promptTemplateId!!,
        promptTemplateVersion = promptTemplateVersion!!,
        promptTemplateHash = hexToBytes(promptTemplateHash!!),
        renderedPrompt = renderedPrompt!!,
        retrievedContext = retrievedContext?.map {
            mapOf("type" to it.type, "id" to it.id, "contentHash" to it.contentHash)
        },
        outcome = outcome!!.name,
        rawOutput = rawOutput,
        verdict = verdict,
        errorDetail = errorDetail,
        latencyMs = latencyMs!!,
        costMicros = costMicros!!,
        costCurrency = costCurrency!!,
    )

    private fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
