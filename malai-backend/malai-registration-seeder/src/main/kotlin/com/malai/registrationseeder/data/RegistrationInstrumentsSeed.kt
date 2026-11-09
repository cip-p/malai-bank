package com.malai.registrationseeder.data

import com.malai.commons.model.Frequency
import com.malai.registrationseeder.data.ClearingAccountsSeed.Clearing

// Instrument templates for a freshly-registered user: a few saved payees, linked
// ACTIVE standing orders, and fixed-amount direct-debit mandates with plausible
// UK creditors. So the register-and-explore persona finds populated payees /
// standing-orders / direct-debit pages immediately, and scheduled activity lands
// while they are still evaluating.
//
// Every creditor / payee reuses a [ClearingAccountsSeed] entry, so the sort codes
// already resolve against the StubFictionalDirectory (federated CoP + FPS-external)
// and no new literal sort code enters the build — the same cross-bank rationale
// ClearingAccountsSeed carries.
//
// `startOffsetDays` is how many days after registration the first payment /
// collection is due; the earliest is one day out so the demo user sees the
// executors fire while they explore. Unlike the canonical Sarah dataset (whose
// dates continue a backdated rhythm), a fresh user has no history, so these anchor
// forward from the registration moment.
object RegistrationInstrumentsSeed {

    data class PayeeTemplate(
        val name: String,
        val clearing: Clearing,
        val nickname: String,
    )

    data class StandingOrderTemplate(
        val reference: String,
        val payeeTemplateIndex: Int,
        val amountMinor: Long,
        val frequency: Frequency,
        val startOffsetDays: Long,
    )

    data class DirectDebitTemplate(
        val creditorName: String,
        val creditor: Clearing,
        val amountMinor: Long,
        val frequency: Frequency,
        val reference: String,
        val startOffsetDays: Long,
    )

    val PAYEES: List<PayeeTemplate> = listOf(
        PayeeTemplate("Eve Robinson", ClearingAccountsSeed.DEMO_PAYEE_1, "Landlord"),
        PayeeTemplate("Transport for London", ClearingAccountsSeed.TFL, "Commute"),
        PayeeTemplate("Tesco Stores Ltd", ClearingAccountsSeed.TESCO, "Groceries"),
    )

    val STANDING_ORDERS: List<StandingOrderTemplate> = listOf(
        StandingOrderTemplate("Rent", payeeTemplateIndex = 0, amountMinor = 85_000L, frequency = Frequency.MONTHLY, startOffsetDays = 2),
        StandingOrderTemplate("Commute top-up", payeeTemplateIndex = 1, amountMinor = 3_000L, frequency = Frequency.WEEKLY, startOffsetDays = 3),
    )

    val DIRECT_DEBITS: List<DirectDebitTemplate> = listOf(
        DirectDebitTemplate("Octopus Energy", ClearingAccountsSeed.OCTOPUS, 4_200L, Frequency.MONTHLY, "Energy", startOffsetDays = 1),
        DirectDebitTemplate("Spotify Premium", ClearingAccountsSeed.SPOTIFY, 1_099L, Frequency.MONTHLY, "Music", startOffsetDays = 2),
        DirectDebitTemplate("Tesco Mobile", ClearingAccountsSeed.TESCO, 1_500L, Frequency.MONTHLY, "Mobile", startOffsetDays = 3),
    )

    const val PAYEE_COUNT: Int = 3
    const val STANDING_ORDER_COUNT: Int = 2
    const val DIRECT_DEBIT_COUNT: Int = 3

    // One SEED_*_CREATED audit event per instrument row — the audit-chain advance
    // assertion in the integration test reads this.
    const val AUDIT_EVENT_COUNT: Int = PAYEE_COUNT + STANDING_ORDER_COUNT + DIRECT_DEBIT_COUNT
}
