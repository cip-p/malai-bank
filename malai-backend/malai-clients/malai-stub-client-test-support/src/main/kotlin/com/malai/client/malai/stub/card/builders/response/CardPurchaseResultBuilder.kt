package com.malai.client.malai.stub.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class CardPurchaseResultBuilder private constructor() : TestBuilder {

    private var schemeReference: String
    private var outcome: String
    private var declineReasonCode: String? = null
    private var includeDeclineReasonCode by Delegates.notNull<Boolean>()

    init {
        includeDeclineReasonCode = false
        schemeReference = "00000000-0000-4000-8000-0000000000ab"
        outcome = "APPROVED"
    }

    fun withSchemeReference(schemeReference: String) = apply { this.schemeReference = schemeReference }
    fun withOutcome(outcome: String) = apply { this.outcome = outcome }
    fun withDeclineReasonCode(declineReasonCode: String) = apply {
        this.declineReasonCode = declineReasonCode
        this.includeDeclineReasonCode = true
    }

    override fun build(): String {
        val base = linkedMapOf<String, Any?>(
            "schemeReference" to schemeReference,
            "outcome" to outcome,
        )
        if (includeDeclineReasonCode) {
            base["declineReasonCode"] = declineReasonCode
        }
        return JsonSerialiser.toJson(base)
    }

    companion object {
        fun aCardPurchaseResult() = CardPurchaseResultBuilder()
    }
}
