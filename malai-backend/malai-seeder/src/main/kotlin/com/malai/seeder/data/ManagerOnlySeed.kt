package com.malai.seeder.data

import java.time.LocalDate
import java.util.UUID

/**
 * The narrow-role demo identity (ADR-0010): an executive who holds ONLY the
 * `manager` Cognito group — never `admin`, never `customer` — so the buyer-side
 * webapp can prove with a Playwright test that a manager-only session lands on
 * the Exec dashboard and nothing else, and the admin MDR API refuses it.
 *
 * Deliberately NOT a customer and NOT an admin: no account holder, individual,
 * address or accounts are seeded. Like the ops reviewer, this identity exists
 * only in Cognito (group `manager`) and the auth `users` table; the absence of a
 * customer estate is what makes the persona inherit the 403/`4999` degrade path
 * for holder-scoped endpoints (three-surfaces issue 06) rather than needing any
 * new contract work.
 *
 * App registration can't produce this identity — the demo policy grants every hat
 * (`customer` + `admin` + `manager`) — so the seeder creates it directly:
 * exclusion from the all-hats grant is structural, the seed simply never calls the
 * `admin` or `customer` group-add for this email.
 *
 * The `+manager` alias delivers to the same demo mailbox as Sarah and the ops
 * reviewer while remaining a distinct Cognito username. [USER_ID] continues the
 * pinned-UUID scheme (index 13) as the local-dev fallback when Cognito is
 * disabled; against a real pool the `users` row keys on the Cognito sub, exactly
 * like the other seeds.
 */
object ManagerOnlySeed {

    val USER_ID: UUID = UUID.fromString("00000000-0000-4000-8000-000000000013")

    const val EMAIL: String = "eleanor.vance@example.com"
    const val FIRST_NAME: String = "Eleanor"
    const val LAST_NAME: String = "Vance"
    const val FULL_NAME: String = "Eleanor Vance"

    val DATE_OF_BIRTH: LocalDate = LocalDate.of(1979, 9, 3)

    // Dev-only demo password, same posture as SarahMitchellSeed.DEMO_PASSWORD.
    const val DEMO_PASSWORD: String = "DemoManager!2026"
}
