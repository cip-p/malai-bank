package com.malai.seeder.commands

import tools.jackson.databind.ObjectMapper
import com.malai.registrationseeder.RegistrationSeederService
import com.malai.registrationseeder.SeedResult
import org.springframework.stereotype.Component
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.util.concurrent.Callable

/**
 * Picocli subcommand `seed-user --email <email>` (M002/S01/T03). Looks up the
 * user by email and calls [RegistrationSeederService.seedFor], printing the
 * resulting [SeedResult] as a single JSON line on stdout so a shell caller can
 * `jq` it. The user must already exist (created via the regular register flow);
 * this command does not touch Cognito and returns `outcome:"user_not_found"`
 * for unknown emails. Idempotent on an account with existing ledger entries —
 * the second call against the same email lands `outcome:"noop"`.
 *
 * Outcome strings mirror the `seeder.registration.seed` structured-log values
 * emitted inside [RegistrationSeederService].
 */
@Component
@Command(
    name = "seed-user",
    description = ["Seed an existing user's CURRENT account with the Sarah-flavour dataset (idempotent)."],
)
class SeedUserCommand(
    private val registrationSeederService: RegistrationSeederService,
    private val objectMapper: ObjectMapper,
) : Callable<Int> {

    @Option(
        names = ["--email"],
        required = true,
        paramLabel = "<email>",
        description = ["Email of an existing user to seed."],
    )
    private lateinit var email: String

    override fun call(): Int {
        val result = registrationSeederService.seedFor(email)
        val payload: Map<String, Any?> = when (result) {
            is SeedResult.Seeded -> mapOf(
                "outcome" to "seeded",
                "userId" to result.userId.toString(),
                "accountId" to result.accountId.toString(),
                "transactionCount" to result.transactionCount,
                "openingBalanceMinor" to result.openingBalanceMinor,
            )
            is SeedResult.Noop -> mapOf(
                "outcome" to "noop",
                "userId" to result.userId.toString(),
                "accountId" to result.accountId.toString(),
                "existingTransactionCount" to result.existingTransactionCount,
            )
            is SeedResult.UserNotFound -> mapOf(
                "outcome" to "user_not_found",
                "email" to result.email,
            )
            is SeedResult.NoActiveCurrentAccount -> mapOf(
                "outcome" to "no_active_current_account",
                "userId" to result.userId.toString(),
            )
        }
        println(objectMapper.writeValueAsString(payload))
        return 0
    }
}
