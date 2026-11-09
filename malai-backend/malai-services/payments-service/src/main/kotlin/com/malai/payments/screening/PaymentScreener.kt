package com.malai.payments.screening

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.ModelDecisionOutcome
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RetrievedContextRef
import com.malai.commons.config.UuidGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class PaymentScreener(
    private val decider: ScreeningDecider,
    private val prompt: PaymentScreeningPrompt,
    private val watchlist: ScreeningWatchlist,
    private val mdrClient: MdrClient,
    private val uuidGenerator: UuidGenerator,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val outputMapper = jacksonObjectMapper()

    fun screen(subject: ScreeningSubject, serviceToken: String): ScreeningResult {
        val decisionId = uuidGenerator.generate()
        val entryId = uuidGenerator.generate()
        val renderedPrompt = prompt.render(subject)

        val startedAtNanos = System.nanoTime()
        var verdict: ScreeningVerdict? = null
        var errorDetail: String? = null
        val outcome = try {
            verdict = decider.decide(subject, renderedPrompt)
            ModelDecisionOutcome.COMPLETED
        } catch (ex: ScreeningTimedOutException) {
            log.warn("screening decider timed out: paymentId={}", subject.paymentId, ex)
            ModelDecisionOutcome.TIMED_OUT
        } catch (ex: Exception) {
            log.error("screening decider failed: paymentId={}", subject.paymentId, ex)
            errorDetail = ex.message ?: ex.javaClass.simpleName
            ModelDecisionOutcome.ERRORED
        }
        val latencyMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

        mdrClient.recordModelDecision(
            serviceToken,
            RecordModelDecisionRequest(
                entryId = entryId,
                decisionId = decisionId,
                correlationType = PAYMENT_CORRELATION_TYPE,
                correlationId = subject.paymentId.toString(),
                modelId = decider.modelId,
                modelVersion = decider.modelVersion,
                promptTemplateId = prompt.templateId,
                promptTemplateVersion = prompt.templateVersion,
                promptTemplateHash = prompt.templateHash,
                renderedPrompt = renderedPrompt,
                retrievedContext = listOf(
                    RetrievedContextRef(
                        type = ACCOUNT_CONTEXT_TYPE,
                        id = subject.payerAccount.accountId.toString(),
                        contentHash = subject.payerAccount.contentHash,
                    ),
                    RetrievedContextRef(
                        type = WATCHLIST_CONTEXT_TYPE,
                        id = watchlist.version,
                        contentHash = watchlist.contentHash,
                    ),
                ),
                outcome = outcome,
                rawOutput = verdict?.let { outputMapper.writeValueAsString(it.asRecordedVerdict()) },
                verdict = verdict?.asRecordedVerdict(),
                errorDetail = errorDetail,
                latencyMs = latencyMs,
                costMicros = 0L,
                costCurrency = COST_CURRENCY,
            ),
        )

        log.info(
            "payment screened: paymentId={} decisionId={} outcome={} decision={} riskScore={}",
            subject.paymentId, decisionId, outcome, verdict?.decision, verdict?.riskScore,
        )

        return ScreeningResult(decisionId = decisionId, verdict = verdict)
    }

    companion object {
        private const val PAYMENT_CORRELATION_TYPE = "PAYMENT"
        private const val ACCOUNT_CONTEXT_TYPE = "ACCOUNT"
        private const val WATCHLIST_CONTEXT_TYPE = "WATCHLIST"
        private const val COST_CURRENCY = "GBP"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
