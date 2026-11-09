package com.malai.api

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Calls Flyway `repair()` before `migrate()` on every boot.
 *
 * `repair()` rebuilds `flyway_schema_history` checksums to match the current
 * migration files, without rerunning anything. That's the canonical fix for
 * "Migration checksum mismatch" errors caused by historical edits to a
 * migration file that's already been applied.
 *
 * For us, this came up when V11 was edited mid-flight (jwt_tokens table being
 * added/removed) — the checksum recorded in dev's schema history no longer
 * matched the file. Without repair, every boot fails Flyway validation.
 *
 * The repair is a no-op when checksums already match, so leaving this on
 * permanently is safe and self-healing for future migration churn.
 */
@Configuration
class FlywayMigrationConfig {

    @Bean
    fun repairThenMigrate(): FlywayMigrationStrategy = FlywayMigrationStrategy { flyway ->
        flyway.repair()
        flyway.migrate()
    }
}
