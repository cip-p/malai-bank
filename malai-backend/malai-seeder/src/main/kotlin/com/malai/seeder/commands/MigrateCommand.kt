package com.malai.seeder.commands

import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import picocli.CommandLine.Command

/**
 * Runs Flyway against the database, applying any pending migrations from
 * `malai-sql`. Constructs the Flyway instance programmatically with the
 * MASTER credentials (not the runtime `malai_app` role) so the V11+ migrations
 * that CREATE ROLE / GRANT can succeed.
 *
 * `--migrate` is idempotent: Flyway tracks applied versions in the
 * `flyway_schema_history` table, so re-running it after a `--clean` (which
 * leaves that table intact) re-validates checksums without re-running migrations.
 */
@Component
@Command(name = "migrate", description = ["Apply pending Flyway migrations using the master DB role."])
class MigrateCommand(
    @Value("\${malai.seeder.master.url}")      private val url: String,
    @Value("\${malai.seeder.master.username}") private val username: String,
    @Value("\${malai.seeder.master.password}") private val password: String,
    @Value("\${malai.seeder.app.password:}")   private val appPassword: String,
) : Runnable {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run() {
        log.info("migrate: running Flyway with master DB role")
        // V12 / V13 use `${malai_app_password}` placeholders (the runtime DB role's
        // password, written into a GRANTed role at migration time). When the env
        // var is missing locally, fall back to the master password — the local
        // docker-compose Postgres uses one role for everything.
        val placeholders = mapOf(
            "malai_app_password" to appPassword.ifBlank { password },
            "db_master_username" to username,
        )
        val flyway = Flyway.configure()
            .dataSource(url, username, password)
            .placeholders(placeholders)
            .load()
        val result = flyway.migrate()
        log.info(
            "migrate: ok migrationsExecuted={} schemaVersion={}",
            result.migrationsExecuted,
            result.targetSchemaVersion,
        )
    }
}
