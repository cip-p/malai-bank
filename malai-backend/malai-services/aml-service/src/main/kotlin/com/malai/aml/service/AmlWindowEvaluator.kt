package com.malai.aml.service

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.aml.monitoring.AlertIdentity
import com.malai.aml.monitoring.AmlDeciderTimedOutException
import com.malai.aml.monitoring.AmlMonitoringDecider
import com.malai.aml.monitoring.AmlMonitoringPrompt
import com.malai.aml.monitoring.AmlWindow
import com.malai.aml.monitoring.MonitoredPayment
import com.malai.aml.monitoring.TypologyHit
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.ModelDecisionOutcome
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RetrievedContextRef
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class AmlWindowEvaluator(
    private val decider: AmlMonitoringDecider,
    private val prompt: AmlMonitoringPrompt,
    private val mdrClient: MdrClient,
    private val caseService: AmlCaseService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)
    private val outputMapper = jacksonObjectMapper()

    fun evaluate(window: AmlWindow, serviceToken: String) {
        val renderedPrompt = prompt.render(window)
        val startedAtNanos = System.nanoTime()
        var hits: List<TypologyHit> = emptyList()
        var errorDetail: String? = null
        val outcome = try {
            hits = decider.decide(window, renderedPrompt)
            ModelDecisionOutcome.COMPLETED
        } catch (ex: AmlDeciderTimedOutException) {
            log.warn("aml decider timed out: accountId={}", window.accountId, ex)
            ModelDecisionOutcome.TIMED_OUT
        } catch (ex: Exception) {
            log.error("aml decider failed: accountId={}", window.accountId, ex)
            errorDetail = ex.message ?: ex.javaClass.simpleName
            ModelDecisionOutcome.ERRORED
        }
        val latencyMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

        if (outcome == ModelDecisionOutcome.COMPLETED) {
            hits.forEach { hit -> raiseAlert(window, hit, renderedPrompt, latencyMs, serviceToken) }
        } else {
            recordEvaluationFailure(window, renderedPrompt, outcome, errorDetail, latencyMs, serviceToken)
        }
    }

    private fun raiseAlert(
        window: AmlWindow,
        hit: TypologyHit,
        renderedPrompt: String,
        latencyMs: Long,
        serviceToken: String,
    ) {
        val evidenceHash = AlertIdentity.evidenceHash(window.accountId, hit.typology, hit.evidence.map { it.paymentId })
        val decisionId = AlertIdentity.alertDecisionId(evidenceHash)
        if (caseService.alertAlreadyRaised(decisionId)) {
            return
        }
        val verdict = mapOf(
            "typology" to hit.typology.name,
            "riskScore" to hit.riskScore,
            "evidencePaymentIds" to hit.evidence.map { it.paymentId.toString() },
        ) + hit.detail
        mdrClient.recordModelDecision(
            serviceToken,
            RecordModelDecisionRequest(
                entryId = AlertIdentity.alertEntryId(evidenceHash),
                decisionId = decisionId,
                correlationType = ACCOUNT_CORRELATION_TYPE,
                correlationId = window.accountId.toString(),
                modelId = decider.modelId,
                modelVersion = decider.modelVersion,
                promptTemplateId = prompt.templateId,
                promptTemplateVersion = prompt.templateVersion,
                promptTemplateHash = prompt.templateHash,
                renderedPrompt = renderedPrompt,
                retrievedContext = evidenceRefs(hit.evidence) + contextPinRefs(hit),
                outcome = ModelDecisionOutcome.COMPLETED,
                rawOutput = outputMapper.writeValueAsString(verdict),
                verdict = verdict,
                latencyMs = latencyMs,
                costMicros = 0L,
                costCurrency = COST_CURRENCY,
            ),
        )
        caseService.attachAlert(window.accountId, decisionId)
    }

    private fun recordEvaluationFailure(
        window: AmlWindow,
        renderedPrompt: String,
        outcome: ModelDecisionOutcome,
        errorDetail: String?,
        latencyMs: Long,
        serviceToken: String,
    ) {
        val failureHash = AlertIdentity.evaluationFailureHash(window.accountId, window.payments.map { it.paymentId })
        mdrClient.recordModelDecision(
            serviceToken,
            RecordModelDecisionRequest(
                entryId = AlertIdentity.evaluationFailureEntryId(failureHash),
                decisionId = AlertIdentity.evaluationFailureDecisionId(failureHash),
                correlationType = ACCOUNT_CORRELATION_TYPE,
                correlationId = window.accountId.toString(),
                modelId = decider.modelId,
                modelVersion = decider.modelVersion,
                promptTemplateId = prompt.templateId,
                promptTemplateVersion = prompt.templateVersion,
                promptTemplateHash = prompt.templateHash,
                renderedPrompt = renderedPrompt,
                retrievedContext = evidenceRefs(window.payments),
                outcome = outcome,
                errorDetail = errorDetail,
                latencyMs = latencyMs,
                costMicros = 0L,
                costCurrency = COST_CURRENCY,
            ),
        )
    }

    private fun evidenceRefs(payments: List<MonitoredPayment>): List<RetrievedContextRef> =
        payments.map {
            RetrievedContextRef(
                type = PAYMENT_CONTEXT_TYPE,
                id = it.paymentId.toString(),
                contentHash = it.contentHash,
            )
        }

    private fun contextPinRefs(hit: TypologyHit): List<RetrievedContextRef> =
        hit.contextPins.map {
            RetrievedContextRef(
                type = it.type,
                id = it.id,
                contentHash = it.contentHash,
            )
        }

    companion object {
        private const val ACCOUNT_CORRELATION_TYPE = "ACCOUNT"
        private const val PAYMENT_CONTEXT_TYPE = "PAYMENT"
        private const val COST_CURRENCY = "GBP"
        private const val NANOS_PER_MILLI = 1_000_000L
    }
}
