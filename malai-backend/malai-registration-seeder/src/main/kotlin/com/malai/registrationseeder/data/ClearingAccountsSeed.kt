package com.malai.registrationseeder.data

import java.util.*


/**
 * Counterparty metadata for the demo seeder's backdated ledger pairs and the
 * payee / direct-debit / standing-order rows. Per-merchant EXTERNAL accounts
 * in the local DB were retired in issue 09; the sort code + account number
 * fields now point at the StubFictionalDirectory entries that the FPS rail +
 * federated CoP consult, so these demo payees resolve via the stub rather
 * than the old V14 rows.
 *
 * The ledger counterparty UUID is the bank's [SCHEME_SETTLEMENT] system
 * account (seeded by V16) -- the demo's backdated transactions are
 * notionally settled FPS legs and net against the bank's scheme position.
 * The merchant name is preserved on the LedgerEntryEntity.merchantName
 * column for UI narrative.
 */
object ClearingAccountsSeed {

    val SYSTEM_USER_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000001")
    val SYSTEM_ACCOUNT_HOLDER_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000002")
    const val SYSTEM_EMAIL: String = "system+clearing@malai-internal.invalid"

    val SCHEME_SETTLEMENT: UUID = UUID.fromString("00000000-0000-4000-8001-000000010002")

    data class Clearing(
        val key: String,
        val name: String,
        val sortCode: String,
        val accountNumber: String,
    )

    val ACME: Clearing = Clearing(
        key = "acme",
        name = "ACME Employer Ltd",
        sortCode = "20-00-05",
        accountNumber = "20000020",
    )
    val TESCO: Clearing = Clearing(
        key = "tesco",
        name = "Tesco Stores Ltd",
        sortCode = "40-00-02",
        accountNumber = "55667788",
    )
    val TFL: Clearing = Clearing(
        key = "tfl",
        name = "Transport for London",
        sortCode = "30-00-03",
        accountNumber = "44556677",
    )
    val OCTOPUS: Clearing = Clearing(
        key = "octopus",
        name = "Octopus Energy Ltd",
        sortCode = "60-00-01",
        accountNumber = "22334455",
    )
    val SPOTIFY: Clearing = Clearing(
        key = "spotify",
        name = "Spotify Limited",
        sortCode = "04-00-04",
        accountNumber = "11335577",
    )
    val DEMO_PAYEE_1: Clearing = Clearing(
        key = "demo",
        name = "Eve Robinson",
        sortCode = "20-00-05",
        accountNumber = "10000010",
    )

    val ALL: List<Clearing> = listOf(ACME, TESCO, TFL, OCTOPUS, SPOTIFY, DEMO_PAYEE_1)

    val BY_KEY: Map<String, Clearing> = ALL.associateBy { it.key }
}
