package com.malai.registrationseeder.hook

import com.malai.auth.service.PostRegistrationHook
import com.malai.registrationseeder.RegistrationSeederService
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Adapter that fulfils [PostRegistrationHook] by delegating to
 * [RegistrationSeederService.seedFor]. Loaded into every app that
 * `@Import`s `RegistrationSeederConfiguration` (malai-api today,
 * malai-seeder for the CLI when wired). The seeder's idempotency
 * guard (≥1 ledger entry → Noop) means re-seeding is a safe no-op,
 * so the hook is free to run on every registration confirmation.
 */
@Component
class SeederPostRegistrationHook(
    private val registrationSeederService: RegistrationSeederService,
) : PostRegistrationHook {

    override fun afterConfirmRegister(userId: UUID, email: String) {
        registrationSeederService.seedFor(email)
    }
}
