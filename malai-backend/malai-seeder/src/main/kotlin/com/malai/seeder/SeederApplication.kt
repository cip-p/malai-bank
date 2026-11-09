package com.malai.seeder

import com.malai.accountholder.AccountHolderServiceConfiguration
import com.malai.accounts.AccountsServiceConfiguration
import com.malai.auth.AuthServiceConfiguration
import com.malai.directdebit.DirectDebitServiceConfiguration
import com.malai.mdr.MdrServiceConfiguration
import com.malai.payee.PayeeServiceConfiguration
import com.malai.registrationseeder.RegistrationSeederConfiguration
import com.malai.standingorder.StandingOrderServiceConfiguration
import org.springframework.boot.Banner
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Import
import picocli.CommandLine
import picocli.spring.PicocliSpringFactory

// Boot entry point for the malai-seeder Picocli CLI (Plan 02-15).
//
// Reuses production code: each per-service `*ServiceConfiguration` carries its
// own scoped @ComponentScan / @EnableJpaRepositories / @EntityScan. Imports are
// explicit (mirrors `MalaiApiApplication`) so the seeder never widens scanning
// onto `com.malai` — this is the standing constraint from Plan 02-11's
// MalaiCommonsConfiguration revert (commits `4819b33` / `391b854` / `7c23636`).
//
// `webApplicationType = NONE` keeps the Spring web stack out of the seeder JVM:
// no embedded Tomcat, no security filter chain. Method-level @PreAuthorize is
// not exercised because the seeder writes via repositories + AuditWriter
// directly, bypassing controller method security.
//
// Flyway-on-boot is disabled — `--migrate` runs Flyway explicitly with the
// master DB role (the runtime malai_app role cannot apply schema changes).
@SpringBootConfiguration
@EnableAutoConfiguration(excludeName = ["org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"])
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
class SeederApplication(
    private val applicationContext: ApplicationContext,
) : CommandLineRunner, ExitCodeGenerator {

    private var exitCode: Int = 0

    override fun run(vararg args: String) {
        val factory = PicocliSpringFactory(applicationContext)
        exitCode = CommandLine(SeederCommand(), factory).execute(*args)
    }

    override fun getExitCode(): Int = exitCode
}

fun main(args: Array<String>) {
    val app = SpringApplication(SeederApplication::class.java).apply {
        setWebApplicationType(WebApplicationType.NONE)
        setBannerMode(Banner.Mode.OFF)
    }
    val exitCode = SpringApplication.exit(app.run(*args))
    System.exit(exitCode)
}
