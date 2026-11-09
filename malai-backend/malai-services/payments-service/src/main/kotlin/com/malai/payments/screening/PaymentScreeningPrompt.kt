package com.malai.payments.screening

import org.springframework.stereotype.Component

@Component
class PaymentScreeningPrompt(private val watchlist: ScreeningWatchlist) {

    private val template: String = javaClass.getResourceAsStream(TEMPLATE_RESOURCE)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("prompt template $TEMPLATE_RESOURCE is missing from the classpath")

    val templateId: String = "payment-screening"

    val templateVersion: String = "v1"

    val templateHash: String = sha256Hex(template)

    fun render(subject: ScreeningSubject): String = template
        .replace("{{paymentId}}", subject.paymentId.toString())
        .replace("{{rail}}", subject.rail.name)
        .replace("{{amountMinorUnits}}", subject.amountMinorUnits.toString())
        .replace("{{currency}}", subject.currency.name)
        .replace("{{beneficiaryName}}", subject.beneficiaryName ?: NOT_SUPPLIED)
        .replace("{{beneficiaryIdentifier}}", subject.beneficiaryIdentifier)
        .replace("{{paymentPurpose}}", subject.paymentPurpose ?: NOT_SUPPLIED)
        .replace("{{payerAccountId}}", subject.payerAccount.accountId.toString())
        .replace("{{payerAccountName}}", subject.payerAccount.name)
        .replace("{{payerAccountIdentifier}}", subject.payerAccount.identifier)
        .replace("{{payerAccountCurrency}}", subject.payerAccount.currency.name)
        .replace("{{watchlistVersion}}", watchlist.version)
        .replace("{{watchlistEntries}}", watchlist.entries)

    companion object {
        private const val TEMPLATE_RESOURCE = "/screening/payment-screening-v1.txt"
        private const val NOT_SUPPLIED = "not supplied"
    }
}
