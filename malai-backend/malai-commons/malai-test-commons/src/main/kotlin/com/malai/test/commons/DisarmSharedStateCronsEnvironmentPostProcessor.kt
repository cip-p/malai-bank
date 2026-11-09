package com.malai.test.commons

import org.springframework.boot.SpringApplication
import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource
import org.springframework.scheduling.annotation.Scheduled

/**
 * Disarms the `@Scheduled` crons whose jobs read and write state the tests themselves own — the
 * audit/MDR hash chains, the `shedlock` row, and `idempotency_keys` in the shared `malai_test`
 * database.
 *
 * Both jobs are exercised by direct invocation (`ChainVerificationJobTest`,
 * `IdempotencyCleanupJobTest`). A scheduler thread firing the same method concurrently takes the
 * ShedLock first, so the test's own call is silently skipped and it asserts on the background
 * run's result instead — the failure is `rowsVerified` 0 instead of 1, and it only appears when a
 * test JVM happens to be alive across the cron's tick, which is why it read as a mid-reactor-only
 * flake. The same thread also logs spurious `CHAIN INTEGRITY FAILURE` lines as it reads a chain
 * that another test class is mid-way through truncating.
 *
 * An [EnvironmentPostProcessor] rather than a line in each module's `application-test.properties`:
 * every Spring context in a test JVM gets this, including the `@SpringTest` contexts that never go
 * through `MalaiAcceptanceTestContext.boot`, whatever order they are created in — and a new
 * acceptance-test module cannot forget it. This module is `test`-scoped everywhere, so no
 * production context ever sees it.
 *
 * Added last so a module that genuinely wants a cron in tests can still set one explicitly.
 */
class DisarmSharedStateCronsEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        environment.propertySources.addLast(
            MapPropertySource(
                PROPERTY_SOURCE_NAME,
                mapOf(
                    "audit.verify.scheduler.cron" to Scheduled.CRON_DISABLED,
                    "idempotency.cleanup.cron" to Scheduled.CRON_DISABLED,
                ),
            )
        )
    }

    companion object {
        const val PROPERTY_SOURCE_NAME = "malaiTestDisarmedCrons"
    }
}
