package com.malai.registrationseeder.testapp

import com.malai.accountholder.AccountHolderServiceConfiguration
import com.malai.accounts.AccountsServiceConfiguration
import com.malai.auth.AuthServiceConfiguration
import com.malai.payee.PayeeServiceConfiguration
import com.malai.registrationseeder.RegistrationSeederConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Import

/**
 * Boot entry point for the malai-registration-seeder integration tests. Mirrors
 * the production SeederApplication pattern: each per-service `*ServiceConfiguration`
 * carries its own scoped @ComponentScan / @EnableJpaRepositories / @EntityScan,
 * so explicit @Import keeps scanning off `com.malai` and matches the standing
 * Plan 02-11 constraint.
 *
 * Flyway is left auto-configured so a fresh `malai_test` database from
 * `start-docker-containers-local.sh` reaches the current schema before any
 * `@SpringBootTest` body runs.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(
    AuthServiceConfiguration::class,
    AccountsServiceConfiguration::class,
    AccountHolderServiceConfiguration::class,
    PayeeServiceConfiguration::class,
    RegistrationSeederConfiguration::class,
)
class TestRegistrationSeederApplication
