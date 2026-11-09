package com.malai.directdebit.service

import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.MANDATE_START_DATE_IN_PAST
import com.malai.commons.model.InstrumentStatus.ACTIVE
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.model.DirectDebitResponse
import com.malai.directdebit.model.LodgeMandateRequest
import com.malai.directdebit.model.toDirectDebitResponse
import com.malai.directdebit.repository.DirectDebitsRepository
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * Lodges a creditor-originated mandate bank-side (the sandbox lodgement path, issue 09). Direct
 * debits are never customer-created, so this is a service-authed internal write with no user
 * principal — the audit actor is SYSTEM (the bank lodging on the creditor's behalf), mirroring the
 * inbound collection settlement audit.
 *
 * `startDate` is the immutable anchor (today or later, "today" from the injected time service in
 * UTC); `nextCollectionAt` is born at the start of `startDate` so a mandate collecting today is
 * immediately due on the collection requester's clock. Fixed-amount mandates only.
 */
@Service
class DirectDebitLodgementService(
    private val repository: DirectDebitsRepository,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun lodge(request: LodgeMandateRequest): DirectDebitResponse {
        val createdAt = timeService.now()

        if (request.startDate.isBefore(LocalDate.ofInstant(createdAt, UTC))) {
            throw BadRequestException(MANDATE_START_DATE_IN_PAST)
        }

        val entity = DirectDebitEntity(
            directDebitId = uuidGenerator.generate(),
            accountId = request.accountId,
            creditorName = request.creditorName,
            creditorSortCode = request.creditorSortCode,
            creditorAccountNumber = request.creditorAccountNumber,
            reference = request.reference,
            amountMinorUnits = request.amountMinorUnits,
            currency = request.currency,
            frequency = request.frequency,
            status = ACTIVE,
            startDate = request.startDate,
            cancelledAt = null,
            nextCollectionAt = request.startDate.atStartOfDay(UTC).toInstant(),
            createdAt = createdAt,
        )
        repository.save(entity)

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "DIRECT_DEBIT_MANDATE_LODGED",
                resourceType = "DirectDebit",
                resourceId = entity.directDebitId.toString(),
                afterState = mapOf(
                    "accountId" to entity.accountId.toString(),
                    "creditorName" to entity.creditorName,
                    "creditorSortCode" to entity.creditorSortCode,
                    "creditorAccountNumber" to entity.creditorAccountNumber,
                    "amountMinorUnits" to entity.amountMinorUnits,
                    "currency" to entity.currency.name,
                    "frequency" to entity.frequency.name,
                    "startDate" to entity.startDate.toString(),
                    "nextCollectionAt" to entity.nextCollectionAt.toString(),
                ),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info(
            "direct debit mandate lodged: directDebitId={} accountId={} creditor={} frequency={} startDate={}",
            entity.directDebitId, entity.accountId, entity.creditorName, entity.frequency, entity.startDate,
        )

        return entity.toDirectDebitResponse()
    }

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
