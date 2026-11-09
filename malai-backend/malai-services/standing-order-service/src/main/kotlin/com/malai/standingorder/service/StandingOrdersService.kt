package com.malai.standingorder.service

import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.ACCOUNT_NOT_FOUND
import com.malai.commons.exception.MalaiCode.PAYEE_NOT_FOUND
import com.malai.commons.exception.MalaiCode.STANDING_ORDER_CURRENCY_NOT_SUPPORTED
import com.malai.commons.exception.MalaiCode.STANDING_ORDER_NOT_FOUND
import com.malai.commons.exception.MalaiCode.STANDING_ORDER_START_DATE_IN_PAST
import com.malai.commons.exception.NotFoundException
import com.malai.commons.model.Currency
import com.malai.commons.model.InstrumentStatus.ACTIVE
import com.malai.commons.model.InstrumentStatus.CANCELLED
import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.standingorder.model.InternalStandingOrderResponse
import com.malai.standingorder.model.InternalStandingOrdersResponse
import com.malai.standingorder.model.StandingOrderRequest
import com.malai.standingorder.model.StandingOrderResponse
import com.malai.standingorder.model.StandingOrdersResponse
import com.malai.standingorder.repository.StandingOrdersRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.util.UUID

/**
 * List + create + cancel. Cancellation is a state transition (status flips to
 * CANCELLED, `cancelled_at` stamped) — rows are never deleted, and the list
 * returns all statuses; presentation policy lives in the webapp.
 *
 * Creation requires a saved payee: name/sort-code/account-number are snapshotted
 * onto the order and execution pays the snapshot. `startDate` is the immutable
 * anchor (today or later, "today" from the injected time service in UTC);
 * `nextPaymentAt` is born at the start of `startDate` so an order paying today
 * is immediately due. GBP only in v1.
 *
 * Ownership is enforced at the repository call (`findByStandingOrderIdAndAccountHolderId`,
 * `accountBelongsToHolder`, `findPayeeSnapshot`); unknown and not-owned ids both
 * surface as 404 to avoid leaking existence — same pattern as PayeesService.
 */
@Service
class StandingOrdersService(
    private val repository: StandingOrdersRepository,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun list(user: MalaiUserWithAccountHolderId): StandingOrdersResponse {
        val rows = repository.findAllByAccountHolderId(user.accountHolderId)
        return StandingOrdersResponse(rows.map(::toResponse))
    }

    fun activeForAccount(accountId: UUID): InternalStandingOrdersResponse {
        val rows = repository.findActiveByAccountId(accountId)
        return InternalStandingOrdersResponse(rows.map(::toInternalResponse))
    }

    @Transactional
    fun create(user: MalaiUserWithAccountHolderId, request: StandingOrderRequest): StandingOrderResponse {
        if (request.currency != Currency.GBP.name) {
            throw BadRequestException(STANDING_ORDER_CURRENCY_NOT_SUPPORTED)
        }

        val createdAt = timeService.now()

        val startDate = request.startDate!!
        if (startDate.isBefore(LocalDate.ofInstant(createdAt, UTC))) {
            throw BadRequestException(STANDING_ORDER_START_DATE_IN_PAST)
        }

        val accountId = request.accountId!!
        if (!repository.accountBelongsToHolder(accountId, user.accountHolderId)) {
            throw NotFoundException(ACCOUNT_NOT_FOUND)
        }

        val payee = repository.findPayeeSnapshot(request.payeeId!!, user.accountHolderId)
            ?: throw NotFoundException(PAYEE_NOT_FOUND)

        val entity = StandingOrderEntity(
            standingOrderId = uuidGenerator.generate(),
            accountId = accountId,
            payeeId = request.payeeId,
            payeeName = payee.name,
            payeeSortCode = payee.sortCode,
            payeeAccountNumber = payee.accountNumber,
            reference = request.reference,
            amountMinorUnits = request.amountMinorUnits,
            currency = Currency.GBP,
            frequency = request.frequency!!,
            status = ACTIVE,
            startDate = startDate,
            nextPaymentAt = startDate.atStartOfDay(UTC).toInstant(),
            createdAt = createdAt,
        )
        repository.save(entity)

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "STANDING_ORDER_CREATED",
                resourceType = "StandingOrder",
                resourceId = entity.standingOrderId.toString(),
                afterState = mapOf(
                    "accountId" to entity.accountId.toString(),
                    "payeeId" to entity.payeeId.toString(),
                    "payeeName" to entity.payeeName,
                    "payeeSortCode" to entity.payeeSortCode,
                    "payeeAccountNumber" to entity.payeeAccountNumber,
                    "amountMinorUnits" to entity.amountMinorUnits,
                    "currency" to entity.currency.name,
                    "frequency" to entity.frequency.name,
                    "startDate" to entity.startDate.toString(),
                    "nextPaymentAt" to entity.nextPaymentAt.toString(),
                ),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info(
            "standing order created: standingOrderId={} accountId={} payeeId={} frequency={} startDate={}",
            entity.standingOrderId, entity.accountId, entity.payeeId, entity.frequency, entity.startDate,
        )

        return toResponse(entity)
    }

    @Transactional
    fun cancel(user: MalaiUserWithAccountHolderId, standingOrderId: UUID) {
        val existing = repository.findByStandingOrderIdAndAccountHolderId(standingOrderId, user.accountHolderId)
            ?: throw NotFoundException(STANDING_ORDER_NOT_FOUND)
        if (existing.status == CANCELLED) return

        val cancelledAt = timeService.now()
        repository.save(existing.copy(status = CANCELLED, cancelledAt = cancelledAt))

        auditWriter.record(
            AuditEvent(
                actorId = user.userId,
                actorType = ActorType.CUSTOMER,
                action = "STANDING_ORDER_CANCELLED",
                resourceType = "StandingOrder",
                resourceId = standingOrderId.toString(),
                beforeState = mapOf("status" to existing.status.name),
                afterState = mapOf("status" to CANCELLED.name, "cancelledAt" to cancelledAt.toString()),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )

        log.info("standing order cancelled: standingOrderId={}", standingOrderId)
    }

    private fun toInternalResponse(entity: StandingOrderEntity) = InternalStandingOrderResponse(
        standingOrderId = entity.standingOrderId,
        accountId = entity.accountId,
        payeeName = entity.payeeName,
        amountMinorUnits = entity.amountMinorUnits,
        currency = entity.currency,
        frequency = entity.frequency,
        status = entity.status,
        startDate = entity.startDate,
    )

    private fun toResponse(entity: StandingOrderEntity) = StandingOrderResponse(
        standingOrderId = entity.standingOrderId,
        accountId = entity.accountId,
        payeeId = entity.payeeId,
        payeeName = entity.payeeName,
        payeeSortCode = entity.payeeSortCode,
        payeeAccountNumber = entity.payeeAccountNumber,
        reference = entity.reference,
        amountMinorUnits = entity.amountMinorUnits,
        currency = entity.currency,
        frequency = entity.frequency,
        status = entity.status,
        startDate = entity.startDate,
        cancelledAt = entity.cancelledAt,
        nextPaymentAt = entity.nextPaymentAt,
    )

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
