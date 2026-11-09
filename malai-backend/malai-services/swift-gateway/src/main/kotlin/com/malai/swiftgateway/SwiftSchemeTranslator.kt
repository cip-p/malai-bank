package com.malai.swiftgateway

import com.malai.payments.messages.SwiftOutboundSettled
import com.malai.payments.messages.SwiftOutboundSubmit
import com.malai.scheme.contracts.swift.SwiftSchemeAmount
import com.malai.scheme.contracts.swift.SwiftSchemeParty
import com.malai.scheme.contracts.swift.SwiftSchemeSettlementCallback
import com.malai.scheme.contracts.swift.SwiftSchemeSubmitRequest

object SwiftSchemeTranslator {

    fun toSchemeSubmit(submit: SwiftOutboundSubmit): SwiftSchemeSubmitRequest =
        SwiftSchemeSubmitRequest(
            endToEndId = submit.endToEndId,
            paymentId = submit.paymentId,
            debtor = SwiftSchemeParty(submit.debtorIban, submit.debtorBic, submit.debtorName),
            creditor = SwiftSchemeParty(submit.beneficiaryIban, submit.beneficiaryBic, submit.beneficiaryName),
            instructedAmount = SwiftSchemeAmount(submit.amountMinorUnits, submit.currency),
            settlementAmount = SwiftSchemeAmount(submit.targetAmountMinorUnits, submit.targetCurrency),
            paymentPurpose = submit.paymentPurpose,
            remittanceInformation = submit.description,
        )

    fun toSettledEvent(callback: SwiftSchemeSettlementCallback): SwiftOutboundSettled =
        SwiftOutboundSettled(paymentId = callback.paymentId, endToEndId = callback.endToEndId)
}
