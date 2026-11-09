package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import com.malai.scheme.contracts.swift.SwiftSchemeAmount
import com.malai.scheme.contracts.swift.SwiftSchemeParty
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitRequest
import java.util.UUID

class SwiftSchemeSubmitRequestBuilder private constructor() : TestBuilder {

    private lateinit var endToEndId: UUID
    private lateinit var paymentId: UUID
    private var debtorIban: String? = "GB82MALA65823710000001"
    private var debtorBic: String? = "MALAGB22XXX"
    private var debtorName: String? = "Alice Walker"
    private var creditorIban: String? = "DE89370400440532013000"
    private var creditorBic: String? = "COBADEFFXXX"
    private var creditorName: String? = "Helga Mueller"
    private var instructedAmountMinorUnits: Long = 10_000L
    private var instructedAmountCurrency: String = "GBP"
    private var settlementAmountMinorUnits: Long = 11_500L
    private var settlementAmountCurrency: String = "EUR"
    private var paymentPurpose: String? = "Rent"
    private var remittanceInformation: String? = "rent in Berlin"

    fun withEndToEndId(endToEndId: UUID) = apply { this.endToEndId = endToEndId }
    fun withPaymentId(paymentId: UUID) = apply { this.paymentId = paymentId }

    override fun build(): String = toJson(
        SwiftSchemeSubmitRequest(
            endToEndId = endToEndId,
            paymentId = paymentId,
            debtor = SwiftSchemeParty(debtorIban, debtorBic, debtorName),
            creditor = SwiftSchemeParty(creditorIban, creditorBic, creditorName),
            instructedAmount = SwiftSchemeAmount(instructedAmountMinorUnits, instructedAmountCurrency),
            settlementAmount = SwiftSchemeAmount(settlementAmountMinorUnits, settlementAmountCurrency),
            paymentPurpose = paymentPurpose,
            remittanceInformation = remittanceInformation,
        )
    )

    companion object {
        fun aSwiftSchemeSubmitRequest() = SwiftSchemeSubmitRequestBuilder()
    }
}
