package com.malai.fpsgateway

import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsInboundReturned
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.payments.messages.ReturnReason
import com.malai.scheme.contracts.fps.FpsReturnReasonCodes
import com.malai.scheme.contracts.fps.FpsSchemeAmount
import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
import com.malai.scheme.contracts.fps.FpsSchemeParty
import com.malai.scheme.contracts.fps.FpsSchemeReturnCallback
import com.malai.scheme.contracts.fps.FpsSchemeReturnToSender
import com.malai.scheme.contracts.fps.FpsSchemeSettlementCallback
import com.malai.scheme.contracts.fps.FpsSchemeSubmitRequest

object FpsSchemeTranslator {

    fun toSchemeSubmit(submit: FpsOutboundSubmit): FpsSchemeSubmitRequest =
        FpsSchemeSubmitRequest(
            endToEndId = submit.endToEndId,
            paymentId = submit.paymentId,
            debtor = FpsSchemeParty(submit.debtorSortCode, submit.debtorAccountNumber, submit.debtorName),
            creditor = FpsSchemeParty(
                submit.beneficiarySortCode,
                submit.beneficiaryAccountNumber,
                submit.beneficiaryName,
            ),
            instructedAmount = FpsSchemeAmount(submit.amountMinorUnits, submit.currency),
            remittanceInformation = submit.description,
            copVerdictOverride = submit.copVerdictOverride,
        )

    fun toSettledEvent(callback: FpsSchemeSettlementCallback): FpsOutboundSettled =
        FpsOutboundSettled(paymentId = callback.paymentId, endToEndId = callback.endToEndId)

    fun toReturnedEvent(callback: FpsSchemeReturnCallback): FpsOutboundReturned =
        FpsOutboundReturned(
            paymentId = callback.paymentId,
            endToEndId = callback.endToEndId,
            reason = ReturnReason.valueOf(FpsReturnReasonCodes.canonicalReasonName(callback.reasonCode)),
        )

    fun toInboundNotify(notification: FpsSchemeInboundNotification): FpsInboundNotify =
        FpsInboundNotify(
            endToEndId = notification.endToEndId,
            beneficiarySortCode = notification.creditor.sortCode!!,
            beneficiaryAccountNumber = notification.creditor.accountNumber!!,
            beneficiaryName = notification.creditor.name!!,
            originatorSortCode = notification.debtor.sortCode!!,
            originatorAccountNumber = notification.debtor.accountNumber!!,
            originatorName = notification.debtor.name!!,
            amountMinorUnits = notification.instructedAmount.minorUnits,
            currency = notification.instructedAmount.currency,
            description = notification.remittanceInformation,
        )

    fun toSchemeReturnToSender(returned: FpsInboundReturned): FpsSchemeReturnToSender =
        FpsSchemeReturnToSender(
            endToEndId = returned.endToEndId,
            creditor = FpsSchemeParty(returned.beneficiarySortCode, returned.beneficiaryAccountNumber, null),
            reasonCode = FpsReturnReasonCodes.schemeCode(returned.reason.name),
        )
}
