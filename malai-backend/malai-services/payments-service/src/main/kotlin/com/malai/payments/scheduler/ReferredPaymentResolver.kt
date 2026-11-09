package com.malai.payments.scheduler

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.client.malai.mdr.MdrClient
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.repository.PaymentRepository
import com.malai.payments.screening.ScreeningDecision
import com.malai.payments.service.PaymentLifecycleService
import com.malai.payments.service.PaymentsServiceTokenClient
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The enforcement half of payment screening. A few-second ShedLock-coordinated sweep of
 * REFERRED payments; each one's stored Model Decision id is presented to the MDR's internal
 * actionable-output gate, and whatever verdict the gate serves is enacted through
 * [PaymentLifecycleService] — APPROVE releases to the rail, DECLINE declines.
 *
 * The gate is the only thing the resolver asks (ADR-0009): it never reads Human Dispositions,
 * so accept/edit/reject → actionable-output translation stays in the MDR. Everything that is
 * not an actionable APPROVE or DECLINE leaves the payment parked, deliberately — a refused gate
 * (no ruling yet, or a reject ruling in force) and output the resolver cannot parse are both
 * the conservative-only invariant of ADR-0008 holding, not failures. A later disposition
 * re-governs a parked payment on a subsequent sweep, because latest-ruling-wins lives in the
 * gate and the payment is still REFERRED for the sweep to find.
 *
 * The gate call happens here, outside the enacting transaction, so no out-of-JVM call is ever
 * made while the audit chain's global head row is locked (the invariant in
 * payments-service/CONTEXT.md). Enactment then fires only from REFERRED, so a re-poll or a
 * retried enactment is naturally idempotent.
 *
 * The poller is vacuous in SHADOW mode — nothing is ever REFERRED there — so it needs no mode
 * gating. In SHADOW the sweep is a single indexed query returning nothing.
 */
@Component
class ReferredPaymentResolver(
    private val paymentRepository: PaymentRepository,
    private val mdrClient: MdrClient,
    private val paymentsServiceTokenClient: PaymentsServiceTokenClient,
    private val paymentLifecycleService: PaymentLifecycleService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val outputMapper = jacksonObjectMapper()

    @Scheduled(cron = "\${malai.payments.screening.resolver.cron:*/5 * * * * *}")
    @SchedulerLock(
        name = LOCK_NAME,
        lockAtMostFor = "\${malai.payments.screening.resolver.lock.at-most-for:4s}",
    )
    fun runOnSchedule() = resolveReferredPayments()

    fun resolveReferredPayments() {
        val referred = paymentRepository.findByStateOrderByCreatedAtAsc(PaymentState.REFERRED)
        if (referred.isEmpty()) return

        val serviceToken = paymentsServiceTokenClient.getToken()
        referred.forEach { payment ->
            try {
                resolve(payment, serviceToken)
            } catch (ex: Exception) {
                log.error(
                    "referred payment resolution failed, leaving it parked for the next sweep: paymentId={}",
                    payment.paymentId, ex,
                )
            }
        }
    }

    private fun resolve(payment: PaymentEntity, serviceToken: String) {
        val decisionId = payment.screeningDecisionId
        if (decisionId == null) {
            log.error("referred payment carries no screening decision id, cannot resolve: paymentId={}", payment.paymentId)
            return
        }

        val actionable = mdrClient.actionableOutput(serviceToken, decisionId)
        if (actionable == null) {
            log.debug("gate serves no actionable output yet, payment stays REFERRED: paymentId={}", payment.paymentId)
            return
        }

        val decision = decisionIn(actionable.actionableOutput, payment.paymentId)
        if (decision == null) {
            log.warn(
                "gate served output the resolver cannot act on, payment stays REFERRED: paymentId={} ruling={}",
                payment.paymentId, actionable.ruling,
            )
            return
        }

        log.info(
            "enacting a ruling on a referred payment: paymentId={} decisionId={} ruling={} decision={}",
            payment.paymentId, decisionId, actionable.ruling, decision,
        )

        when (decision) {
            ScreeningDecision.DECLINE -> paymentLifecycleService.transitionToDeclined(payment.paymentId)
            ScreeningDecision.APPROVE -> paymentLifecycleService.transitionToReleased(payment.paymentId)
        }
    }

    private fun decisionIn(actionableOutput: String, paymentId: UUID): ScreeningDecision? =
        runCatching {
            outputMapper.readTree(actionableOutput).get(DECISION_FIELD)?.asString()
                ?.let { ScreeningDecision.valueOf(it) }
        }.getOrElse {
            log.warn("actionable output is not a verdict the resolver understands: paymentId={}", paymentId)
            null
        }

    companion object {
        const val LOCK_NAME = "payments-referred-resolver"
        private const val DECISION_FIELD = "decision"
    }
}
