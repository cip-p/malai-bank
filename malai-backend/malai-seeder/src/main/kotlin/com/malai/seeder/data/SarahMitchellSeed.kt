package com.malai.seeder.data

import java.time.LocalDate
import java.util.*

// Demo customer fixture. All IDs are deterministic so the same `--reset` run
// produces an identical dataset, which lets the buyer-side webapp pin against
// known UUIDs in screenshots / Storybook demos.
object SarahMitchellSeed {

    val USER_ID: UUID            = UUID.fromString("00000000-0000-4000-8000-000000000001")
    val ACCOUNT_HOLDER_ID: UUID  = UUID.fromString("00000000-0000-4000-8000-000000000002")
    val CURRENT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000003")
    val SAVINGS_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000004")
    val INDIVIDUAL_ID: UUID      = UUID.fromString("00000000-0000-4000-8000-000000000005")
    val ADDRESS_ID: UUID      = UUID.fromString("00000000-0000-4000-8000-000000000006")

    // The flagship credit-card estate (cards issue 12). A CREDIT_CARD account with its own
    // pinned id + card, months of settled merchant history and two live pending
    // authorisations. Ids follow the same pinned scheme (index 7+) so the webapp can screenshot
    // against them.
    val CREDIT_ACCOUNT_ID: UUID  = UUID.fromString("00000000-0000-4000-8000-000000000007")
    val CREDIT_CARD_ID: UUID     = UUID.fromString("00000000-0000-4000-8000-000000000008")
    val PENDING_AUTH_1_ID: UUID  = UUID.fromString("00000000-0000-4000-8000-000000000009")
    val PENDING_AUTH_2_ID: UUID  = UUID.fromString("00000000-0000-4000-8000-00000000000a")
    val PENDING_AUTH_1_SCHEME_REF: UUID = UUID.fromString("00000000-0000-4000-8000-00000000000b")
    val PENDING_AUTH_2_SCHEME_REF: UUID = UUID.fromString("00000000-0000-4000-8000-00000000000c")

    const val EMAIL: String = "sarah.mitchell@example.com"
    const val FIRST_NAME: String = "Sarah"
    const val LAST_NAME: String = "Mitchell"
    const val FULL_NAME: String = "Sarah Mitchell"

    // Single source of truth for the demo customer's DoB — feeds both the
    // auth-service `users` row and the account-holder-service `individuals` row
    // so they can never drift.
    val DATE_OF_BIRTH: LocalDate = LocalDate.of(1990, 6, 15)

    // Ofcom-reserved London fictional-example range (020 7946 0000–0999) — see
    // https://www.ofcom.org.uk/phones-and-broadband/phone-numbers/numbers-for-drama .
    // Guaranteed never to connect to a real subscriber; safe to ship in demo
    // datasets and screenshots.
    const val PHONE: String = "+44 20 7946 0000"

    // Dev-only demo password. Documented in 02-15-SUMMARY; rotate per Phase 5.
    const val DEMO_PASSWORD: String = "DemoCustomer!2026"

    // Sarah's account numbers — chosen under the MALAI 65-82 prefix. Pay.UK VALACDOS
    // has zero rules covering 65-XX-XX (verified 2026-05-06), so every 8-digit
    // candidate is Vocalink-valid. AccountNumberGenerator's modulus guard would
    // accept these too if we ever re-seeded through it.
    const val CURRENT_ACCOUNT_NUMBER: String = "12345678"
    const val SAVINGS_ACCOUNT_NUMBER: String = "12345679"
    const val CREDIT_ACCOUNT_NUMBER: String = "12345680"

    // Fixed RNG seed the CLI hands to RegistrationSeederService.seedFor(email, seed)
    // so Sarah's CURRENT-account dataset (amounts, count, opening balance, instrument
    // + transaction UUIDs) reproduces byte-for-byte on every `--reset`, while a real
    // self-registered user still gets a fresh random dataset.
    const val SEED: Long = 20_260_708L

    // Sarah's SAVINGS account is a seeder-only extra (a real registration onboards a
    // single CURRENT account). One D-30 opening posting lands the balance at £8400.00.
    const val SAVINGS_OPENING_MINOR: Long = 840_000L

    // Dedicated RNG seed for the flagship CREDIT card's PAN/CVV — kept off the CURRENT-account
    // Random(SEED) stream so the credit PAN is stable across re-seeds (the ambient scheduler
    // targets it) without perturbing the byte-stable CURRENT dataset.
    const val CREDIT_CARD_PAN_SEED: Long = 20_260_712L

    // The flagship credit history nets to owed > £200 so the StatementCalculator's 2.5%
    // minimum-payment branch fires (not the £5 floor): settled merchant purchases totalling
    // £440.00, less a £90.00 partial repayment, leave the credit account's ledger balance at
    // −£350.00 (owed £350.00, minimum £8.75). "Part-paid current statement", non-trivially.
    const val CREDIT_REPAYMENT_MINOR: Long = 9_000L
    const val CREDIT_OWED_MINOR: Long = 35_000L
}
