package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import com.malai.scheme.contracts.fps.FpsSchemeParty
import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import java.util.UUID

class FpsSchemeReturnToSenderBuilder private constructor() : TestBuilder {

    private lateinit var endToEndId: UUID
    private var creditorSortCode: String? = "60-00-01"
    private var creditorAccountNumber: String? = "12345678"
    private var creditorName: String? = null
    private var reasonCode: String = "AC01"

    fun withEndToEndId(endToEndId: UUID) = apply { this.endToEndId = endToEndId }
    fun withCreditorSortCode(sortCode: String?) = apply { this.creditorSortCode = sortCode }
    fun withCreditorAccountNumber(accountNumber: String?) = apply { this.creditorAccountNumber = accountNumber }
    fun withReasonCode(reasonCode: String) = apply { this.reasonCode = reasonCode }

    override fun build(): String = toJson(
        FpsSchemeReturnToSender(
            endToEndId = endToEndId,
            creditor = FpsSchemeParty(creditorSortCode, creditorAccountNumber, creditorName),
            reasonCode = reasonCode,
        )
    )

    companion object {
        fun anFpsSchemeReturnToSender() = FpsSchemeReturnToSenderBuilder()
    }
}
