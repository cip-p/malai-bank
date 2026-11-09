package com.malai.directdebit.service

import com.malai.commons.TimeService
import com.malai.commons.exception.MalaiCode.MANDATE_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import com.malai.commons.model.InstrumentStatus.CANCELLED
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.model.DirectDebitsResponse
import com.malai.directdebit.model.InternalDirectDebitResponse
import com.malai.directdebit.model.InternalDirectDebitsResponse
import com.malai.directdebit.model.toDirectDebitResponse
import com.malai.directdebit.repository.DirectDebitsRepository
import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * List + cancel. Cancellation is a state transition (status flips to CANCELLED,
 * `cancelled_at` stamped) — rows are never deleted, and the list returns all
 * statuses; presentation policy (greyed-out Cancelled rows) lives in the webapp.
 *
 * Ownership is enforced at the repository call (`findByDirectDebitIdAndAccountHolderId`);
 * unknown and not-owned ids both surface as 404 to avoid leaking existence —
 * same pattern as StandingOrdersService.
 */
@Service
class DirectDebitsService(
    private val repository: DirectDebitsRepository,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(user: MalaiUserWithAccountHolderId): DirectDebitsResponse {
        val rows = repository.findAllByAccountHolderId(user.accountHolderId)
        return DirectDebitsResponse(rows.map(DirectDebitEntity::toDirectDebitResponse))
    }

    fun activeForAccount(accountId: UUID): InternalDirectDebitsResponse {
        val rows = repository.findActiveByAccountId(accountId)
        return InternalDirectDebitsResponse(rows.map(::toInternalResponse))
    }

    private fun toInternalResponse(entity: DirectDebitEntity) = InternalDirectDebitResponse(
        directDebitId = entity.directDebitId,
        accountId = entity.accountId,
        creditorName = entity.creditorName,
        amountMinorUnits = entity.amountMinorUnits,
        currency = entity.currency,
        frequency = entity.frequency,
        status = entity.status,
        startDate = entity.startDate,
    )

    @Transactional
    fun cancel(user: MalaiUserWithAccountHolderId, directDebitId: UUID) {
        val existing = repository.findByDirectDebitIdAndAccountHolderId(directDebitId, user.accountHolderId)
            ?: throw NotFoundException(MANDATE_NOT_FOUND)
        if (existing.status == CANCELLED) return

        val cancelledAt = timeService.now()
        repository.save(existing.copy(status = CANCELLED, cancelledAt = cancelledAt))

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "DIRECT_DEBIT_CANCELLED",
                resourceType = "DirectDebit",
                resourceId = directDebitId.toString(),
                beforeState = mapOf("status" to existing.status.name),
                afterState = mapOf("status" to CANCELLED.name, "cancelledAt" to cancelledAt.toString()),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info("direct debit cancelled: directDebitId={}", directDebitId)
    }

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
