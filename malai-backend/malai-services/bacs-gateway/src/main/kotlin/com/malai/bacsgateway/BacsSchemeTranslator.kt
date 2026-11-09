package com.malai.bacsgateway

import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsCollectionReturn
import com.malai.payments.messages.BacsInboundNotify
import com.malai.scheme.contracts.bacs.BacsReturnReasonCodes
import com.malai.scheme.contracts.bacs.BacsSchemeCollectionReturn
import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import com.malai.scheme.contracts.bacs.BacsSchemeParty

object BacsSchemeTranslator {

    fun toInboundNotify(credit: BacsSchemeDirectCredit): BacsInboundNotify =
        BacsInboundNotify(
            endToEndId = credit.endToEndId,
            beneficiarySortCode = credit.creditor.sortCode,
            beneficiaryAccountNumber = credit.creditor.accountNumber,
            beneficiaryName = credit.creditor.name,
            originatorSortCode = credit.originator.sortCode,
            originatorAccountNumber = credit.originator.accountNumber,
            originatorName = credit.originator.name,
            amountMinorUnits = credit.instructedAmount.minorUnits,
            currency = credit.instructedAmount.currency,
            description = credit.remittanceInformation,
        )

    fun toCollectionNotify(collection: BacsSchemeDirectDebitCollection): BacsCollectionNotify =
        BacsCollectionNotify(
            endToEndId = collection.endToEndId,
            debtorSortCode = collection.debtor.sortCode,
            debtorAccountNumber = collection.debtor.accountNumber,
            debtorName = collection.debtor.name,
            creditorSortCode = collection.creditor.sortCode,
            creditorAccountNumber = collection.creditor.accountNumber,
            creditorName = collection.creditor.name,
            amountMinorUnits = collection.instructedAmount.minorUnits,
            currency = collection.instructedAmount.currency,
            description = collection.remittanceInformation,
        )

    fun toSchemeCollectionReturn(returned: BacsCollectionReturn): BacsSchemeCollectionReturn =
        BacsSchemeCollectionReturn(
            endToEndId = returned.endToEndId,
            creditor = BacsSchemeParty(
                sortCode = returned.creditorSortCode,
                accountNumber = returned.creditorAccountNumber,
                name = returned.creditorName,
            ),
            reasonCode = BacsReturnReasonCodes.schemeCode(returned.reason.name),
        )
}
