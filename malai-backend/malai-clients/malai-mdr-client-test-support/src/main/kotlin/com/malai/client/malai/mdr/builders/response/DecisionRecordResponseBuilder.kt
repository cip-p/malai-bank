package com.malai.client.malai.mdr.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID
import kotlin.properties.Delegates

class DecisionRecordResponseBuilder private constructor() : TestBuilder {

    private lateinit var decisionId: UUID
    private lateinit var entryId: UUID
    private var seq by Delegates.notNull<Long>()
    private lateinit var ts: String
    private lateinit var outcome: String
    private var advisory by Delegates.notNull<Boolean>()
    private lateinit var correlationType: String
    private lateinit var correlationId: String
    private lateinit var modelId: String
    private lateinit var modelVersion: String
    private var verdict: Map<String, Any?>? = null
    private var retrievedContext: List<ContextRef>? = null
    private var errorDetail: String? = null
    private var rulingInForce: String? = null

    fun withDecisionId(decisionId: UUID) = apply { this.decisionId = decisionId }
    fun withEntryId(entryId: UUID) = apply { this.entryId = entryId }
    fun withSeq(seq: Long) = apply { this.seq = seq }
    fun withTs(ts: String) = apply { this.ts = ts }
    fun withOutcome(outcome: String) = apply { this.outcome = outcome }
    fun withAdvisory(advisory: Boolean) = apply { this.advisory = advisory }
    fun withCorrelationType(correlationType: String) = apply { this.correlationType = correlationType }
    fun withCorrelationId(correlationId: String) = apply { this.correlationId = correlationId }
    fun withModelId(modelId: String) = apply { this.modelId = modelId }
    fun withModelVersion(modelVersion: String) = apply { this.modelVersion = modelVersion }
    fun withVerdict(verdict: Map<String, Any?>?) = apply { this.verdict = verdict }
    fun withErrorDetail(errorDetail: String?) = apply { this.errorDetail = errorDetail }
    fun withRulingInForce(rulingInForce: String?) = apply { this.rulingInForce = rulingInForce }

    fun withRetrievedContext(vararg refs: ContextRefBuilder) = apply {
        this.retrievedContext = refs.map { it.build() }
    }

    override fun build(): String = JsonSerialiser.toJson(
        DecisionRecord(
            decisionId = decisionId,
            entryId = entryId,
            seq = seq,
            ts = ts,
            outcome = outcome,
            advisory = advisory,
            correlationType = correlationType,
            correlationId = correlationId,
            modelId = modelId,
            modelVersion = modelVersion,
            verdict = verdict,
            retrievedContext = retrievedContext,
            errorDetail = errorDetail,
            rulingInForce = rulingInForce,
        ),
    )

    class ContextRefBuilder private constructor() {

        private lateinit var type: String
        private lateinit var id: String
        private lateinit var contentHash: String

        fun withType(type: String) = apply { this.type = type }
        fun withId(id: String) = apply { this.id = id }
        fun withContentHash(contentHash: String) = apply { this.contentHash = contentHash }

        fun build() = ContextRef(type, id, contentHash)

        companion object {
            fun aPaymentContextRef(paymentId: UUID, contentHash: String) = ContextRefBuilder()
                .withType("PAYMENT")
                .withId(paymentId.toString())
                .withContentHash(contentHash)
        }
    }

    data class DecisionRecord(
        val decisionId: UUID,
        val entryId: UUID,
        val seq: Long,
        val ts: String,
        val outcome: String,
        val advisory: Boolean,
        val correlationType: String,
        val correlationId: String,
        val modelId: String,
        val modelVersion: String,
        val verdict: Map<String, Any?>?,
        val retrievedContext: List<ContextRef>?,
        val errorDetail: String?,
        val rulingInForce: String?,
    )

    data class ContextRef(
        val type: String,
        val id: String,
        val contentHash: String,
    )

    companion object {
        fun aDecisionRecordResponse() = DecisionRecordResponseBuilder()
            .withDecisionId(UUID.randomUUID())
            .withEntryId(UUID.randomUUID())
            .withSeq(1L)
            .withTs("2026-10-15T10:30:00Z")
            .withOutcome("COMPLETED")
            .withAdvisory(false)
            .withCorrelationType("ACCOUNT")
            .withCorrelationId(UUID.randomUUID().toString())
            .withModelId("malai-aml-rules")
            .withModelVersion("1")
    }
}
