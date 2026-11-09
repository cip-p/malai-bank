package com.malai.client.malai.mdr.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID
import kotlin.properties.Delegates

class RecordModelDecisionResponseBuilder private constructor() : TestBuilder {

    private lateinit var entryId: UUID
    private lateinit var decisionId: UUID
    private var seq by Delegates.notNull<Long>()
    private lateinit var outcome: String

    fun withEntryId(entryId: UUID) = apply { this.entryId = entryId }
    fun withDecisionId(decisionId: UUID) = apply { this.decisionId = decisionId }
    fun withSeq(seq: Long) = apply { this.seq = seq }
    fun withOutcome(outcome: String) = apply { this.outcome = outcome }

    override fun build(): String =
        JsonSerialiser.toJson(RecordModelDecision(entryId, decisionId, seq, outcome))

    data class RecordModelDecision(
        val entryId: UUID,
        val decisionId: UUID,
        val seq: Long,
        val outcome: String,
    )

    companion object {
        fun aRecordModelDecisionResponse() = RecordModelDecisionResponseBuilder()
            .withEntryId(UUID.randomUUID())
            .withDecisionId(UUID.randomUUID())
            .withSeq(1L)
            .withOutcome("COMPLETED")
    }
}
