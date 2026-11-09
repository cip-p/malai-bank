package com.malai.standingorder.service

import com.malai.client.malai.payments.MalaiPaymentsClient
import com.malai.client.malai.payments.PaymentInsufficientFundsException
import com.malai.client.malai.payments.model.InternalInitiatePaymentRequest
import com.malai.commons.schedule.OccurrenceSchedule
import com.malai.commons.schedule.ScheduledPaymentEndToEndId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.standingorder.repository.StandingOrdersRepository
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * Executes a single due occurrence of a standing order, then advances its schedule — the
 * per-order half of the executor, split out so the transactional boundary applies through the
 * Spring proxy when the scheduler ([com.malai.standingorder.scheduler.StandingOrderExecutor])
 * invokes it.
 *
 * Initiate first, advance second (ADR 0004): the occurrence pays the payee snapshot through the
 * payments internal seam with a deterministic end-to-end id derived from the order id and the
 * occurrence date, so a re-fire after a crash between the two steps bounces harmlessly off the
 * payments unique key. After firing exactly once the schedule resynchronises to the first
 * occurrence strictly after "now" — downtime never produces catch-up bursts. Insufficient funds
 * is a missed occurrence: audited, never retried, the schedule still advances and the order stays
 * ACTIVE. Any other initiation error propagates so the caller leaves the schedule unadvanced to
 * retry on the next run.
 */
@Service
class StandingOrderExecutionService(
    private val repository: StandingOrdersRepository,
    private val paymentsClient: MalaiPaymentsClient,
    private val serviceTokenClient: StandingOrderServiceTokenClient,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun execute(order: StandingOrderEntity, asOf: Instant) {
        val occurrenceDate = LocalDate.ofInstant(order.nextPaymentAt!!, UTC)
        val accountHolderId = repository.findAccountHolderId(order.accountId)
            ?: throw IllegalStateException("no account holder resolves for account ${order.accountId}")
        val endToEndId = ScheduledPaymentEndToEndId.forOccurrence(order.standingOrderId, occurrenceDate)
        val nextPaymentDate =
            OccurrenceSchedule.nextStrictlyAfter(order.startDate, order.frequency, LocalDate.ofInstant(asOf, UTC))

        try {
            paymentsClient.initiateScheduledPayment(
                serviceTokenClient.getToken(),
                InternalInitiatePaymentRequest(
                    endToEndId = endToEndId,
                    fromAccountId = order.accountId,
                    accountHolderId = accountHolderId,
                    toSortCode = order.payeeSortCode,
                    toAccountNumber = order.payeeAccountNumber,
                    toName = order.payeeName,
                    amountMinorUnits = order.amountMinorUnits,
                    currency = order.currency.name,
                    reference = order.reference,
                ),
            )
        } catch (ex: PaymentInsufficientFundsException) {
            recordMissedOccurrence(order, occurrenceDate)
        }

        repository.save(order.copy(nextPaymentAt = nextPaymentDate.atStartOfDay(UTC).toInstant()))

        log.info(
            "standing order executed: standingOrderId={} occurrence={} nextPayment={}",
            order.standingOrderId, occurrenceDate, nextPaymentDate,
        )
    }

    private fun recordMissedOccurrence(order: StandingOrderEntity, occurrenceDate: LocalDate) {
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "STANDING_ORDER_PAYMENT_MISSED",
                resourceType = "StandingOrder",
                resourceId = order.standingOrderId.toString(),
                afterState = mapOf(
                    "occurrenceDate" to occurrenceDate.toString(),
                    "reason" to "INSUFFICIENT_FUNDS",
                    "amountMinorUnits" to order.amountMinorUnits,
                ),
                traceId = MDC.get(MDC_TRACE_ID),
            ),
        )
        log.warn(
            "standing order occurrence missed (insufficient funds), advancing schedule: standingOrderId={} occurrence={}",
            order.standingOrderId, occurrenceDate,
        )
    }

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
