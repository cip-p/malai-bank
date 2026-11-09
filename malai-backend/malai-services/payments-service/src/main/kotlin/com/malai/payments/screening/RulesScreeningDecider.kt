package com.malai.payments.screening

import org.springframework.stereotype.Component

@Component
class RulesScreeningDecider(
    private val properties: ScreeningProperties,
    private val watchlist: ScreeningWatchlist,
) : ScreeningDecider {

    override val modelId: String = "malai-screening-rules"

    override val modelVersion: String = "1"

    override fun decide(subject: ScreeningSubject, renderedPrompt: String): ScreeningVerdict {
        if (subject.amountMinorUnits == properties.armedTimeoutAmountMinorUnits) {
            throw ScreeningTimedOutException("screening decider timed out after ${subject.amountMinorUnits} minor units")
        }

        val watchlisted = watchlist.flags(subject.beneficiaryName)
        val atOrAboveThreshold = subject.amountMinorUnits >= properties.thresholdMinorUnits

        val reasons = buildList {
            if (atOrAboveThreshold) add(AMOUNT_AT_OR_ABOVE_THRESHOLD)
            if (watchlisted) add(WATCHLIST_COUNTERPARTY)
        }

        return ScreeningVerdict(
            decision = if (reasons.isEmpty()) ScreeningDecision.APPROVE else ScreeningDecision.DECLINE,
            riskScore = riskScore(subject.amountMinorUnits, watchlisted),
            reasons = reasons,
        )
    }

    private fun riskScore(amountMinorUnits: Long, watchlisted: Boolean): Int {
        val band = when {
            amountMinorUnits < 100_000L -> 10
            amountMinorUnits < 500_000L -> 25
            amountMinorUnits < 1_000_000L -> 45
            else -> 80
        }
        return if (watchlisted) minOf(99, band + WATCHLIST_UPLIFT) else band
    }

    companion object {
        private const val AMOUNT_AT_OR_ABOVE_THRESHOLD = "AMOUNT_AT_OR_ABOVE_THRESHOLD"
        private const val WATCHLIST_COUNTERPARTY = "WATCHLIST_COUNTERPARTY"
        private const val WATCHLIST_UPLIFT = 15
    }
}
