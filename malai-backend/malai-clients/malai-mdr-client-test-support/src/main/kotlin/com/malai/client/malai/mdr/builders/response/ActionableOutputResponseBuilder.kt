package com.malai.client.malai.mdr.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class ActionableOutputResponseBuilder private constructor() : TestBuilder {

    private lateinit var decisionId: UUID
    private lateinit var ruling: String
    private lateinit var rulingEntryId: UUID
    private lateinit var actionableOutput: String

    fun withDecisionId(decisionId: UUID) = apply { this.decisionId = decisionId }
    fun withRuling(ruling: String) = apply { this.ruling = ruling }
    fun withRulingEntryId(rulingEntryId: UUID) = apply { this.rulingEntryId = rulingEntryId }
    fun withActionableOutput(actionableOutput: String) = apply { this.actionableOutput = actionableOutput }

    override fun build(): String =
        JsonSerialiser.toJson(ActionableOutput(decisionId, ruling, rulingEntryId, actionableOutput))

    data class ActionableOutput(
        val decisionId: UUID,
        val ruling: String,
        val rulingEntryId: UUID,
        val actionableOutput: String,
    )

    companion object {
        fun anActionableOutputResponse() = ActionableOutputResponseBuilder()
            .withDecisionId(UUID.randomUUID())
            .withRuling("ACCEPT")
            .withRulingEntryId(UUID.randomUUID())
            .withActionableOutput("APPROVE: low risk.")
    }
}
