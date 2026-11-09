package com.malai.auth.service

import java.util.UUID

/**
 * Port invoked by [RegistrationOrchestrator] at the tail of a successful
 * `confirmRegister` — after Cognito-confirm, the user-row flip to confirmed,
 * and the cross-service activations have all returned. Adapters wire side
 * effects that depend on the user being fully active: e.g. seeding the new
 * CURRENT account with demo-quality transactions.
 *
 * Auth-service ships zero implementations; host apps wire adapters via their
 * own Spring configuration. Hooks run synchronously in the caller's thread,
 * iterated in Spring's default bean order. Failures bubble — a failing hook
 * fails the registration call, on purpose: a hook is contract, not garnish.
 */
interface PostRegistrationHook {
    fun afterConfirmRegister(userId: UUID, email: String)
}
