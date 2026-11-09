package com.malai.stub.scheme

import com.malai.payments.messages.ReturnReason
import com.malai.scheme.contracts.fps.FpsReturnReasonCodes
import com.malai.scheme.contracts.fps.FpsSchemeReturnCallback
import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import com.malai.scheme.contracts.fps.FpsSchemeSettlementCallback
import com.malai.scheme.contracts.fps.FpsSchemeSubmitAck
import com.malai.scheme.contracts.fps.FpsSchemeSubmitRequest
import com.malai.stub.directory.OutboundOutcome
import com.malai.stub.directory.OutboundOutcomeResolver
import com.malai.stub.directory.StubFictionalDirectory
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

@Component
class StubFpsScheme(
    private val directory: StubFictionalDirectory,
    private val outboundOutcomeResolver: OutboundOutcomeResolver,
    private val gatewayClient: StubFpsGatewayClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val processedEndToEndIds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val receivedReturns: CopyOnWriteArrayList<FpsSchemeReturnToSender> = CopyOnWriteArrayList()
    private val armedReturn: AtomicReference<ReturnReason?> = AtomicReference(null)

    fun armReturn(reason: ReturnReason) {
        armedReturn.set(reason)
        log.info("stub fps: armed one-shot return with reason={}", reason)
    }

    fun submit(request: FpsSchemeSubmitRequest): FpsSchemeSubmitAck {
        if (!processedEndToEndIds.add(request.endToEndId.toString())) {
            log.info("stub fps: ignoring duplicate submit endToEndId={}", request.endToEndId)
            return ackFor(request)
        }
        when (val decision = decide(request.copVerdictOverride, request.creditor.sortCode, request.creditor.accountNumber)) {
            FpsDecision.Settle -> {
                gatewayClient.notifySettled(
                    FpsSchemeSettlementCallback(endToEndId = request.endToEndId, paymentId = request.paymentId),
                )
                log.info("stub fps: settled paymentId={} endToEndId={}", request.paymentId, request.endToEndId)
            }
            is FpsDecision.Return -> {
                gatewayClient.notifyReturned(
                    FpsSchemeReturnCallback(
                        endToEndId = request.endToEndId,
                        paymentId = request.paymentId,
                        reasonCode = FpsReturnReasonCodes.schemeCode(decision.reason.name),
                    ),
                )
                log.info(
                    "stub fps: returned paymentId={} endToEndId={} reason={}",
                    request.paymentId, request.endToEndId, decision.reason,
                )
            }
            FpsDecision.StuckPending -> {
                log.info(
                    "stub fps: stuck pending paymentId={} endToEndId={} (no settlement, no return)",
                    request.paymentId, request.endToEndId,
                )
            }
        }
        return ackFor(request)
    }

    fun receiveReturnToSender(request: FpsSchemeReturnToSender) {
        receivedReturns.add(request)
        log.info(
            "stub fps: received return-to-sender endToEndId={} beneficiary={}/{} reasonCode={}",
            request.endToEndId, request.creditor.sortCode, request.creditor.accountNumber, request.reasonCode,
        )
    }

    fun receivedReturns(): List<FpsSchemeReturnToSender> = receivedReturns.toList()

    fun hasReceivedReturnFor(endToEndId: UUID): Boolean = receivedReturns.any { it.endToEndId == endToEndId }

    fun decide(
        copVerdictOverride: Boolean,
        beneficiarySortCode: String?,
        beneficiaryAccountNumber: String?,
    ): FpsDecision {
        if (copVerdictOverride) {
            return FpsDecision.Return(ReturnReason.ACCOUNT_NAME_MISMATCH)
        }

        armedReturn.getAndSet(null)?.let { return FpsDecision.Return(it) }

        if (beneficiarySortCode == null || beneficiaryAccountNumber == null) {
            return FpsDecision.Settle
        }

        return when (outboundOutcomeResolver.resolve(beneficiarySortCode, beneficiaryAccountNumber)) {
            OutboundOutcome.SETTLES -> FpsDecision.Settle
            OutboundOutcome.RETURNS -> {
                val reason = directory.find(beneficiarySortCode, beneficiaryAccountNumber)?.returnReason
                    ?: error("directory invariant violated: RETURNS outcome with null returnReason")
                FpsDecision.Return(reason)
            }
            OutboundOutcome.STUCK_PENDING -> FpsDecision.StuckPending
        }
    }

    private fun ackFor(request: FpsSchemeSubmitRequest) =
        FpsSchemeSubmitAck(endToEndId = request.endToEndId, paymentId = request.paymentId)
}

sealed interface FpsDecision {
    data object Settle : FpsDecision
    data class Return(val reason: ReturnReason) : FpsDecision
    data object StuckPending : FpsDecision
}
