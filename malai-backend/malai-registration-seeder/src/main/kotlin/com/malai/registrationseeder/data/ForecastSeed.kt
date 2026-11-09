package com.malai.registrationseeder.data

import com.malai.registrationseeder.data.ClearingAccountsSeed.Clearing

/**
 * Instrument templates for the warning-tripping balance forecast every freshly
 * self-registered user receives (balance-forecast issue 03).
 *
 * Unlike [RegistrationInstrumentsSeed] — which anchors a fresh user's instruments
 * a day or three in the *future* and is what the canonical Sarah Mitchell demo
 * keeps unchanged — these straddle *today*: two occurrences already completed
 * earlier this month, each with a matching backdated ledger posting so the
 * available balance reflects them and they surface in the transactions feed, and
 * two still expected before month-end. The expected "Rent" standing order is the
 * one instrument whose amount is not fixed here: the seeder sizes it off the
 * account's post-completion available balance so the projected month-end balance
 * always lands [OVERSHOOT_MINOR] in the red — the "you look fine now, but you
 * won't be by month-end" moment the forecast exists to show.
 *
 * Every instrument is MONTHLY, so each contributes exactly one occurrence to the
 * current calendar month, and every counterparty reuses a [ClearingAccountsSeed]
 * entry so no new literal sort code enters the build. The month-boundary caveat
 * the SO/DD e2e tests already live with applies: on the final calendar day of the
 * month there is no room left after today for an expected occurrence, so on that
 * one day the Expected list is empty and the projected balance is not negative.
 */
object ForecastSeed {

    data class ForecastPayee(val name: String, val clearing: Clearing, val nickname: String)

    data class ForecastStandingOrder(
        val reference: String,
        val payeeIndex: Int,
        val amountMinor: Long,
        val description: String,
        val postingType: String,
        val daysFromToday: Long,
    )

    data class ForecastDirectDebit(
        val creditorName: String,
        val creditor: Clearing,
        val reference: String,
        val amountMinor: Long,
        val description: String,
        val daysFromToday: Long,
    )

    val PAYEES: List<ForecastPayee> = listOf(
        ForecastPayee("Eve Robinson", ClearingAccountsSeed.DEMO_PAYEE_1, "Landlord"),
        ForecastPayee("Transport for London", ClearingAccountsSeed.TFL, "Commute"),
    )

    val COMPLETED_STANDING_ORDERS: List<ForecastStandingOrder> = listOf(
        ForecastStandingOrder(
            reference = "Commute",
            payeeIndex = 1,
            amountMinor = 3_000L,
            description = UkMerchantsSeed.TFL_COMMUTE,
            postingType = "PAYMENT_SETTLEMENT",
            daysFromToday = -3,
        ),
    )

    val COMPLETED_DIRECT_DEBITS: List<ForecastDirectDebit> = listOf(
        ForecastDirectDebit(
            creditorName = ClearingAccountsSeed.OCTOPUS.name,
            creditor = ClearingAccountsSeed.OCTOPUS,
            reference = "Energy",
            amountMinor = 4_200L,
            description = UkMerchantsSeed.OCTOPUS_DD,
            daysFromToday = -6,
        ),
    )

    val EXPECTED_DIRECT_DEBITS: List<ForecastDirectDebit> = listOf(
        ForecastDirectDebit(
            creditorName = ClearingAccountsSeed.SPOTIFY.name,
            creditor = ClearingAccountsSeed.SPOTIFY,
            reference = "Music",
            amountMinor = 1_099L,
            description = UkMerchantsSeed.SPOTIFY_DD,
            daysFromToday = 2,
        ),
    )

    // The balance-relative dip. payeeIndex 0 is the landlord; its amount is
    // recomputed by the seeder, so the constant here is a placeholder never used.
    val EXPECTED_RENT: ForecastStandingOrder = ForecastStandingOrder(
        reference = "Rent",
        payeeIndex = 0,
        amountMinor = 0L,
        description = UkMerchantsSeed.RENT_SO,
        postingType = "PAYMENT_SETTLEMENT",
        daysFromToday = 1,
    )

    // How far below zero the projected month-end balance lands once every expected
    // occurrence is subtracted from the available balance (£250).
    const val OVERSHOOT_MINOR: Long = 25_000L

    val EXPECTED_FIXED_MAGNITUDE_MINOR: Long = EXPECTED_DIRECT_DEBITS.sumOf { it.amountMinor }

    const val PAYEE_COUNT: Int = 2
    const val STANDING_ORDER_COUNT: Int = 2
    const val DIRECT_DEBIT_COUNT: Int = 2
    const val COMPLETED_POSTING_COUNT: Int = 2

    const val INSTRUMENT_AUDIT_EVENT_COUNT: Int = PAYEE_COUNT + STANDING_ORDER_COUNT + DIRECT_DEBIT_COUNT
}
