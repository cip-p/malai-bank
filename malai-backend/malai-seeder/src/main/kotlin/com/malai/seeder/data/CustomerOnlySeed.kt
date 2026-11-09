package com.malai.seeder.data

import java.time.LocalDate
import java.util.UUID

/**
 * The real-bank surface-gating persona: a demo customer who holds ONLY the
 * `customer` Cognito group — never `admin` — so the buyer-side webapp can prove
 * with a Playwright test that a customer-only session sees no staff surfaces
 * and the admin API refuses it.
 *
 * App registration can't produce this user (the registration demo policy grants
 * every hat), so the seeder creates him directly: exclusion from the demo grant
 * is structural — the seed simply never calls the admin group-add.
 *
 * The `+customer` alias delivers to the same demo mailbox as Sarah and the ops
 * reviewer. Pinned UUIDs continue the scheme at index 0e; against a real pool
 * the `users` row keys on the Cognito sub, exactly like the other seeds.
 */
object CustomerOnlySeed {

    val USER_ID: UUID           = UUID.fromString("00000000-0000-4000-8000-00000000000e")
    val ACCOUNT_HOLDER_ID: UUID = UUID.fromString("00000000-0000-4000-8000-00000000000f")
    val CURRENT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000010")
    val INDIVIDUAL_ID: UUID     = UUID.fromString("00000000-0000-4000-8000-000000000011")
    val ADDRESS_ID: UUID        = UUID.fromString("00000000-0000-4000-8000-000000000012")

    const val EMAIL: String = "oliver.bennett@example.com"
    const val FIRST_NAME: String = "Oliver"
    const val LAST_NAME: String = "Bennett"
    const val FULL_NAME: String = "Oliver Bennett"

    val DATE_OF_BIRTH: LocalDate = LocalDate.of(1993, 4, 22)

    // Same Ofcom-reserved fictional range as Sarah's PHONE, next number along.
    const val PHONE: String = "+44 20 7946 0001"

    // Dev-only demo password, same posture as SarahMitchellSeed.DEMO_PASSWORD.
    const val DEMO_PASSWORD: String = "DemoCustomerOnly!2026"

    // Next free number in Sarah's 65-82-prefix sequence (see her fixture's
    // VALACDOS note — every 8-digit candidate under the prefix is valid).
    const val CURRENT_ACCOUNT_NUMBER: String = "12345681"

    // Fixed RNG seed for RegistrationSeederService.seedFor(email, seed) so this
    // user's CURRENT-account dataset reproduces byte-for-byte on every reset,
    // exactly like Sarah's SEED.
    const val SEED: Long = 20_260_719L
}
