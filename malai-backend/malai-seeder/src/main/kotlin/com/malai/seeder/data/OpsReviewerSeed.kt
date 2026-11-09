package com.malai.seeder.data

import java.time.LocalDate
import java.util.UUID

/**
 * Back-office demo identity: the ops reviewer who logs into the admin surface
 * (MDR decision worklist, chain verification, the forthcoming ops pages).
 *
 * Deliberately NOT a customer — no account holder, individual, address or
 * accounts are seeded; back-office staff exist only in Cognito (group `admin`)
 * and the auth `users` table, and the absence of a customer estate is itself
 * demo-accurate.
 *
 * The `+ops` alias delivers to the same demo mailbox as Sarah while remaining
 * a distinct Cognito username. [USER_ID] continues Sarah's pinned-UUID scheme
 * (index 0d) as the local-dev fallback when Cognito is disabled; against a real
 * pool the `users` row keys on the Cognito sub, exactly like Sarah's.
 */
object OpsReviewerSeed {

    val USER_ID: UUID = UUID.fromString("00000000-0000-4000-8000-00000000000d")

    const val EMAIL: String = "priya.sharma@example.com"
    const val FIRST_NAME: String = "Priya"
    const val LAST_NAME: String = "Sharma"
    const val FULL_NAME: String = "Priya Sharma"

    val DATE_OF_BIRTH: LocalDate = LocalDate.of(1987, 2, 11)

    // Dev-only demo password, same posture as SarahMitchellSeed.DEMO_PASSWORD.
    const val DEMO_PASSWORD: String = "DemoOps!2026"
}
