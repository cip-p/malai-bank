package com.malai.stub.scheme

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.scheme.contracts.card.CardSchemeAmount
import com.malai.scheme.contracts.card.CardSchemeAuthorisationOutcome.APPROVED
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.scheme.contracts.card.CardSchemeAuthorisationResponse
import com.malai.scheme.contracts.card.CardSchemePresentmentRequest
import com.malai.scheme.contracts.card.CardSchemeRefundRequest
import com.malai.scheme.contracts.card.CardSchemeReversalRequest
import com.malai.stub.directory.StubMerchantDirectory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Component
class StubCardScheme(
    private val merchants: StubMerchantDirectory,
    private val gatewayClient: StubCardGatewayClient,
    @Value("\${malai.stub.card.present-delay-millis:30000}")
    private val presentDelayMillis: Long,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val pending = ConcurrentHashMap<UUID, OriginatedAuthorisation>()
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "stub-card-present").apply { isDaemon = true }
    }

    fun originateAuthorisation(
        pan: String,
        merchantId: String,
        amountMinorUnits: Long,
        currency: String,
    ): CardSchemeAuthorisationResponse {
        val merchant = merchants.find(merchantId)
            ?: throw IllegalArgumentException("unknown merchant: $merchantId")

        val request = CardSchemeAuthorisationRequest(
            schemeReference = UUID.randomUUID(),
            pan = pan,
            amount = CardSchemeAmount(amountMinorUnits, currency),
            merchantName = merchant.name,
            mcc = merchant.mcc,
        )

        val response = gatewayClient.authorise(request)
        log.info(
            "stub card scheme: authorised schemeReference={} merchant={} amount={} outcome={}",
            request.schemeReference, merchant.name, amountMinorUnits, response.outcome,
        )

        if (response.outcome == APPROVED) {
            pending[request.schemeReference] = OriginatedAuthorisation(merchant.name, amountMinorUnits, currency)
            val testId = TestIdContext.valueOrNull
            scheduler.schedule(
                { presentOnSchedulerThread(request.schemeReference, testId) },
                presentDelayMillis,
                TimeUnit.MILLISECONDS,
            )
        }

        return response
    }

    fun presentAllPending(): Int {
        val references = pending.keys.toList()
        references.forEach(::present)
        return references.size
    }

    fun reverse(schemeReference: UUID) {
        pending.remove(schemeReference)
        gatewayClient.reverse(CardSchemeReversalRequest(schemeReference = schemeReference))
        log.info("stub card scheme: reversed schemeReference={}", schemeReference)
    }

    fun refund(schemeReference: UUID, amountMinorUnits: Long, currency: String) {
        val refundReference = UUID.randomUUID()
        gatewayClient.refund(
            CardSchemeRefundRequest(
                schemeReference = schemeReference,
                refundReference = refundReference,
                amount = CardSchemeAmount(amountMinorUnits, currency),
            ),
        )
        log.info(
            "stub card scheme: refunded schemeReference={} refundReference={} amount={}",
            schemeReference, refundReference, amountMinorUnits,
        )
    }

    private fun presentOnSchedulerThread(schemeReference: UUID, testId: TestId?) {
        if (testId != null) TestIdContext.value = testId
        try {
            present(schemeReference)
        } catch (e: Exception) {
            log.warn("stub card scheme: scheduled presentment failed schemeReference={}", schemeReference, e)
        } finally {
            if (testId != null) TestIdContext.unset()
        }
    }

    private fun present(schemeReference: UUID) {
        val originated = pending.remove(schemeReference) ?: return
        gatewayClient.present(
            CardSchemePresentmentRequest(
                schemeReference = schemeReference,
                amount = CardSchemeAmount(originated.amountMinorUnits, originated.currency),
                merchantName = originated.merchantName,
            ),
        )
        log.info(
            "stub card scheme: presented schemeReference={} merchant={} amount={}",
            schemeReference, originated.merchantName, originated.amountMinorUnits,
        )
    }

    private data class OriginatedAuthorisation(
        val merchantName: String,
        val amountMinorUnits: Long,
        val currency: String,
    )
}
