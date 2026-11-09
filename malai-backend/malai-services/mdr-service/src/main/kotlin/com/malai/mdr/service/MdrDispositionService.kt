package com.malai.mdr.service

import com.malai.commons.exception.MalaiCode.MDR_DECISION_NOT_FOUND
import com.malai.commons.exception.MalaiCode.MDR_DISPOSITION_TARGET_NOT_A_DECISION
import com.malai.commons.exception.NotFoundException
import com.malai.commons.exception.UnprocessableEntityException
import com.malai.mdr.model.RecordHumanDispositionRequest
import com.malai.mdr.model.RecordHumanDispositionResponse
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

// Appends a Human Disposition ruling on a prior Model Decision, then records an ordinary audit
// event -- the same deliberate double-entry as the decision path: the MDR chain answers "what was
// ruled", the audit chain answers "who did what". The ruling human's id comes from the
// authenticated admin principal, never the request body.
//
// The parent Model Decision is resolved from the path's decisionId, not supplied -- the earliest
// entry for the decision is always the Model Decision (it is appended before any ruling can exist).
// An unknown decisionId is a 404; a decisionId whose earliest entry is not a Model Decision (a
// ruling can never target a ruling) is a 422. Idempotency is natural on the client-supplied
// entry_id, exactly as on the decision path: a replayed append returns the existing Entry.
@Service
class MdrDispositionService(
    private val jdbc: JdbcTemplate,
    private val mdrEntryWriter: MdrEntryWriter,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun recordDisposition(
        decisionId: UUID,
        humanId: UUID,
        request: RecordHumanDispositionRequest,
    ): RecordHumanDispositionResponse {
        val existing = findExistingDisposition(request.entryId!!)
        if (existing != null) {
            log.debug("mdr: disposition {} already recorded at seq {} -- returning existing", existing.entryId, existing.seq)
            return existing
        }

        val decision = resolveDecision(decisionId)

        val appended = mdrEntryWriter.recordDisposition(
            RecordedHumanDisposition(
                entryId = request.entryId,
                decisionId = decisionId,
                parentEntryId = decision.entryId,
                correlationType = decision.correlationType,
                correlationId = decision.correlationId,
                humanId = humanId,
                disposition = request.disposition!!.name,
                editedOutput = request.editedOutput,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = humanId,
                actorType = ActorType.ADMIN,
                action = "MDR_DISPOSITION_RECORDED",
                resourceType = "ModelDecision",
                resourceId = decisionId.toString(),
                afterState = mapOf(
                    "entryId" to request.entryId.toString(),
                    "disposition" to request.disposition.name,
                ),
            ),
        )

        log.info("mdr: recorded {} disposition on decision {} at chain seq {}", request.disposition, decisionId, appended.seq)
        return RecordHumanDispositionResponse(
            entryId = request.entryId,
            decisionId = decisionId,
            parentEntryId = decision.entryId,
            seq = appended.seq,
            disposition = request.disposition.name,
        )
    }

    private fun resolveDecision(decisionId: UUID): ResolvedDecision {
        val row = jdbc.query(
            """
            SELECT entry_id, entry_kind, correlation_type, correlation_id
            FROM mdr_entries WHERE decision_id = ? ORDER BY seq ASC LIMIT 1
            """.trimIndent(),
            { rs, _ ->
                ResolvedDecision(
                    entryId = rs.getObject("entry_id", UUID::class.java),
                    entryKind = rs.getString("entry_kind"),
                    correlationType = rs.getString("correlation_type"),
                    correlationId = rs.getString("correlation_id"),
                )
            },
            decisionId,
        ).firstOrNull() ?: throw NotFoundException(MDR_DECISION_NOT_FOUND)

        if (row.entryKind != "MODEL_DECISION") {
            throw UnprocessableEntityException(MDR_DISPOSITION_TARGET_NOT_A_DECISION)
        }
        return row
    }

    private fun findExistingDisposition(entryId: UUID): RecordHumanDispositionResponse? =
        jdbc.query(
            """
            SELECT entry_id, decision_id, parent_entry_id, seq, disposition
            FROM mdr_entries WHERE entry_id = ? AND entry_kind = 'HUMAN_DISPOSITION'
            """.trimIndent(),
            { rs, _ ->
                RecordHumanDispositionResponse(
                    entryId = rs.getObject("entry_id", UUID::class.java),
                    decisionId = rs.getObject("decision_id", UUID::class.java),
                    parentEntryId = rs.getObject("parent_entry_id", UUID::class.java),
                    seq = rs.getLong("seq"),
                    disposition = rs.getString("disposition"),
                )
            },
            entryId,
        ).firstOrNull()

    private data class ResolvedDecision(
        val entryId: UUID,
        val entryKind: String,
        val correlationType: String,
        val correlationId: String,
    )
}
