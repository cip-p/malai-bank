package com.malai.commons.model

/**
 * Lifecycle of an onboarding-time entity (account-holder, individual, account).
 *
 * Rows are inserted as `PENDING` by the auth-service's register orchestrator and flipped to
 * `ACTIVE` once the user confirms their email. The DB column has a default of `ACTIVE` so
 * legacy code paths that don't yet thread the field continue to work — see V17 migration.
 */
enum class OnboardingStatus {
    PENDING,
    ACTIVE,
}
