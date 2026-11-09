package com.malai.aml.service

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.aml.model.AmlSarDraftResponse
import com.malai.aml.model.SarRefusalCode
import com.malai.aml.sar.SarDraftBudget
import com.malai.aml.sar.SarDraftDecider
import com.malai.aml.sar.SarDraftOutcome
import com.malai.aml.sar.SarDraftTimedOutException
import com.malai.aml.sar.SarGroundingValidator
import com.malai.aml.sar.SarNarrativePrompt
import com.malai.aml.sar.SarStatement
import com.malai.client.malai.mdr.MdrClient
import com.malai.client.malai.mdr.model.ModelDecisionOutcome
import com.malai.client.malai.mdr.model.RecordModelDecisionRequest
import com.malai.client.malai.mdr.model.RetrievedContextRef
import com.malai.commons.config.UuidGenerator
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SarDraftService(
    private val caseService: AmlCaseService,
    private val timelineAssembler: SarTimelineAssembler,
    private val prompt: SarNarrativePrompt,
    private val decider: SarDraftDecider,
    private val budget: SarDraftBudget,
    private val grounding: SarGroundingValidator,
    private val mdrClient: MdrClient,
    private val uuidGenerator: UuidGenerator,
    private val serviceTokenClient: AmlServiceTokenClient,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    private val outputMapper = jacksonObjectMapper()

    fun requestDraft(caseId: UUID): AmlSarDraftResponse {
        val amlCase = caseService.anOpenCase(caseId)
        val decisionId = uuidGenerator.generate()
        val entryId = uuidGenerator.generate()
        val timeline = timelineAssembler.assemble(amlCase)
        val renderedPrompt = prompt.render(timeline)

        val startedAtNanos = System.nanoTime()
        var produced: SarDraftOutcome? = null
        var errorDetail: String? = null
        val outcome = try {
            produced = budget.within { decider.draft(timeline, renderedPrompt) }
            ModelDecisionOutcome.COMPLETED
        } catch (ex: SarDraftTimedOutException) {
            log.warn("sar drafter timed out: caseId={}", caseId, ex)
            ModelDecisionOutcome.TIMED_OUT
        } catch (ex: Exception) {
            log.error("sar drafter failed: caseId={}", caseId, ex)
            errorDetail = ex.message?.takeIf { it.isNotBlank() } ?: ex.javaClass.simpleName
            ModelDecisionOutcome.ERRORED
        }
        val latencyMs = (System.nanoTime() - startedAtNanos) / NANOS_PER_MILLI

        val statements = produced?.statements
        val grounded = statements != null && grounding.isGrounded(statements, timeline)

        mdrClient.recordModelDecision(
            serviceTokenClient.getToken(),
            RecordModelDecisionRequest(
                entryId = entryId,
                decisionId = decisionId,
                correlationType = CASE_CORRELATION_TYPE,
                correlationId = caseId.toString(),
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
                rawOutput = statements?.let { outputMapper.writeValueAsString(it.asRecordedVerdict()) },
                verdict = if (grounded) statements.asRecordedVerdict() else null,
                errorDetail = errorDetail,
                latencyMs = latencyMs,
                costMicros = produced?.costMicros ?: 0L,
                costCurrency = decider.costCurrency,
            ),
        )

        caseService.attachSarDraft(caseId, decisionId)

        log.info(
            "sar draft concluded: caseId={} decisionId={} outcome={} statements={} grounded={}",
            caseId, decisionId, outcome, statements?.size, grounded,
        )

        return AmlSarDraftResponse(
            decisionId = decisionId,
            outcome = outcome.name,
            narrative = if (grounded) statements.asNarrative() else null,
            refusalCode = if (statements != null && !grounded) SarRefusalCode.GROUNDING_FAILED else null,
        )
    }

    private fun List<SarStatement>.asNarrative(): String = joinToString("\n\n") { it.text }

    private fun List<SarStatement>.asRecordedVerdict(): Map<String, Any?> = mapOf(
        "narrative" to asNarrative(),
        "statements" to map { statement ->
            mapOf(
                "text" to statement.text,
                "sources" to statement.sources.map { mapOf("type" to it.type, "id" to it.id) },
            )
        },
    )

    private companion object {
        const val CASE_CORRELATION_TYPE = "CASE"
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
