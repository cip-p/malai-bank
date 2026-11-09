package com.malai.seeder.testapp

import com.malai.accountholder.AccountHolderServiceConfiguration
import com.malai.accounts.AccountsServiceConfiguration
import com.malai.auth.AuthServiceConfiguration
import com.malai.directdebit.DirectDebitServiceConfiguration
import com.malai.mdr.MdrServiceConfiguration
import com.malai.payee.PayeeServiceConfiguration
import com.malai.registrationseeder.RegistrationSeederConfiguration
import com.malai.seeder.SeederConfiguration
import com.malai.standingorder.StandingOrderServiceConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Import

/**
 * Boot entry point for the malai-seeder integration tests. Mirrors the production
 * [com.malai.seeder.SeederApplication] imports (each per-service `*ServiceConfiguration`
 * carries its own scoped @ComponentScan / @EnableJpaRepositories / @EntityScan) minus the
 * Picocli CommandLineRunner, so a test can autowire [com.malai.seeder.commands.SeedCommand]
 * and drive it directly.
 *
 * Unlike production, Flyway is left auto-configured (the production app runs `--migrate`
 * explicitly with the master role) so a fresh `malai_test` from
 * `start-docker-containers-local.sh` reaches the current schema before any test body runs.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(
    AuthServiceConfiguration::class,
    AccountsServiceConfiguration::class,
    AccountHolderServiceConfiguration::class,
    PayeeServiceConfiguration::class,
    DirectDebitServiceConfiguration::class,
    StandingOrderServiceConfiguration::class,
    MdrServiceConfiguration::class,
    RegistrationSeederConfiguration::class,
    SeederConfiguration::class,
)
class TestSeederApplication
