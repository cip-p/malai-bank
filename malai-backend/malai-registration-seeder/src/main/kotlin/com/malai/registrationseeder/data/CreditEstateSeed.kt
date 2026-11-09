package com.malai.registrationseeder.data

/**
 * Fixture data for the credit-card and savings half of a registered customer's
 * estate.
 *
 * These values mirror the canonical Sarah Mitchell dataset in malai-seeder's
 * SeedCommand, deliberately duplicated rather than shared. Sarah's path is
 * byte-stable — fixed UUIDs and a fixed RNG seed so the webapp can pin
 * screenshots and Storybook against it — and routing her through this code
 * would put that stability at the mercy of every change made for
 * self-registered users. The duplication is the cheaper risk.
 *
 * What is NOT duplicated is identity: Sarah's UUIDs stay in SarahMitchellSeed.
 * Everything here is shape and money only, so a registrant gets the same
 * estate with their own ids.
 */
object CreditEstateSeed {

    const val CREDIT_ACCOUNT_NAME = "Credit Card"
    const val SAVINGS_ACCOUNT_NAME = "Savings"

    /** Credit limit as a ledger floor: settled purchases push the balance negative down to this. */
    const val CREDIT_FLOOR_MINOR = -250_000L

    /** The "part-paid" in a part-paid current statement. */
    const val CREDIT_REPAYMENT_MINOR = 9_000L

    const val SAVINGS_OPENING_MINOR = 840_000L

    data class CreditPurchase(val merchantName: String, val amountMinor: Long, val daysAgo: Long)

    /**
     * Settled merchant purchases on the credit account, using the stub's
     * fictional merchant directory so seeded history and ambient card spend
     * read as one estate. Total £440.00; less the £90.00 repayment they net to
     * £350.00 owed — over £200, so the 2.5% minimum-payment branch fires.
     * Spread across the last three cycles for "several months" of history.
     */
    val PURCHASES: List<CreditPurchase> = listOf(
        CreditPurchase("Atlas Electronics", 22_000L, 70),
        CreditPurchase("Nimbus Groceries", 8_600L, 55),
        CreditPurchase("Harbour Fuel", 6_500L, 40),
        CreditPurchase("Fictional Coffee Co", 1_450L, 20),
        CreditPurchase("Orbit Transit", 4_451L, 10),
        CreditPurchase("Lumen Streaming", 999L, 5),
    )

    /**
     * Live, unsettled authorisations on the CURRENT account so the feed shows
     * PENDING rows on first open (£52.00 + £18.75 = £70.75 held).
     *
     * No ids here, unlike Sarah's equivalent: each registrant's authorisation
     * and scheme-reference UUIDs are generated at seed time. Only the shape is
     * fixed.
     */
    data class PendingAuthorisation(
        val merchantName: String,
        val mcc: String,
        val amountMinor: Long,
        val minutesAgo: Long,
    )

    val PENDING_AUTHORISATIONS: List<PendingAuthorisation> = listOf(
        PendingAuthorisation("Harbour Fuel", "5541", 5_200L, minutesAgo = 24 * 60),
        PendingAuthorisation("Nimbus Groceries", "5411", 1_875L, minutesAgo = 6 * 60),
    )
}
