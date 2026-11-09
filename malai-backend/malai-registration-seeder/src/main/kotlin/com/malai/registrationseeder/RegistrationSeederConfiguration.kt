package com.malai.registrationseeder

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

// Bounded @ComponentScan so the registration-seeder beans (LedgerSeedHelper today,
// RegistrationSeederService next) are discovered by every host app that @Imports
// this configuration — malai-api for the HTTP endpoint and the
// RegistrationOrchestrator auto-call, malai-seeder for the CLI subcommand —
// without widening scanning onto the umbrella com.malai package (Plan 02-11
// anti-pattern).
@Configuration
@ComponentScan(basePackages = ["com.malai.registrationseeder"])
class RegistrationSeederConfiguration
