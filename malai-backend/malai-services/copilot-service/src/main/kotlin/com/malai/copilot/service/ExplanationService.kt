package com.malai.copilot.service

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.ModelDecisionOutcome
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RetrievedContextRef
import com.malai.commons.config.UuidGenerator
import com.malai.copilot.model.ClaimResponse
import com.malai.copilot.model.ExplanationResponse
import com.malai.copilot.model.RefusalCode
import com.malai.copilot.model.Timeline
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ExplanationService(
    private val timelineAssembler: PaymentTimelineAssembler,
    private val prompt: CopilotPaymentPrompt,
    private val decider: CopilotDecider,
    private val budget: DeciderBudget,
    private val grounding: GroundingValidator,
    private val mdrClient: MdrClient,
    private val uuidGenerator: UuidGenerator,
    private val serviceTokenClient: CopilotServiceTokenClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    private val outputMapper = jacksonObjectMapper()

    fun explainPayment(paymentId: UUID): ExplanationResponse {
        val decisionId = uuidGenerator.generate()
        val entryId = uuidGenerator.generate()
        val timeline = timelineAssembler.assemble(paymentId)
        val renderedPrompt = prompt.render(timeline)

        val startedAtNanos = System.nanoTime()
        var produced: DeciderOutcome? = null
        var errorDetail: String? = null
        val outcome = try {
            produced = budget.within { decider.explain(timeline, renderedPrompt) }
            ModelDecisionOutcome.COMPLETED
        } catch (ex: CopilotTimedOutException) {
            log.warn("copilot decider timed out: paymentId={}", paymentId, ex)
            ModelDecisionOutcome.TIMED_OUT
        } catch (ex: Exception) {
            log.error("copilot decider failed: paymentId={}", paymentId, ex)
            errorDetail = ex.message?.takeIf { it.isNotBlank() } ?: ex.javaClass.simpleName
            ModelDecisionOutcome.ERRORED
        }
        val latencyMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

        val claims = produced?.claims
        val grounded = claims != null && grounding.isGrounded(claims, timeline)

        mdrClient.recordModelDecision(
            serviceTokenClient.getToken(),
            RecordModelDecisionRequest(
                entryId = entryId,
                decisionId = decisionId,
                correlationType = timeline.subjectType,
                correlationId = paymentId.toString(),
                advisory = true,
                modelId = decider.modelId,
                modelVersion = produced?.modelVersion ?: decider.modelVersion,
                promptTemplateId = prompt.templateId,
                promptTemplateVersion = prompt.templateVersion,
                promptTemplateHash = prompt.templateHash,
                renderedPrompt = renderedPrompt,
                retrievedContext = timeline.records.map {
                    RetrievedContextRef(type = it.type.name, id = it.id, contentHash = it.contentHash)
                },
                outcome = outcome,
                rawOutput = claims?.let { outputMapper.writeValueAsString(it.asRecordedVerdict()) },
                verdict = if (grounded) claims.asRecordedVerdict() else null,
                errorDetail = errorDetail,
                latencyMs = latencyMs,
                costMicros = produced?.costMicros ?: 0L,
                costCurrency = decider.costCurrency,
            ),
        )

        log.info(
            "explanation concluded: subject={} paymentId={} decisionId={} outcome={} claims={} grounded={}",
            timeline.subjectType, paymentId, decisionId, outcome, claims?.size, grounded,
        )

        return ExplanationResponse(
            decisionId = decisionId,
            outcome = outcome.name,
            claims = if (grounded) claims.orEmpty().map { ClaimResponse(it.text, it.sources) } else emptyList(),
            refusalCode = if (claims != null && !grounded) RefusalCode.GROUNDING_FAILED else null,
        )
    }

    private fun List<Claim>.asRecordedVerdict(): Map<String, Any?> = mapOf(
        "claims" to map { claim ->
            mapOf(
                "text" to claim.text,
                "sources" to claim.sources.map { mapOf("type" to it.type, "id" to it.id) },
            )
        },
    )

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
