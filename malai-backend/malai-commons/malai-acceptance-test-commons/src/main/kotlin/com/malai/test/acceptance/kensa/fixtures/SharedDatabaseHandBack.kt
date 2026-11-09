package com.malai.test.acceptance.kensa.fixtures

import com.malai.sql.MalaiSchema
import com.malai.test.acceptance.MalaiAcceptanceTestContext

/**
 * Hands `malai_test` back at post-migration state once a test JVM that borrowed it has finished.
 *
 * [deleteAllFixtures] clears `users`, `accounts`, `account_holders` and `ledger_accounts` among
 * others, which drops the rows V14 seeds — the system clearing user, the three suspense accounts
 * and their `ledger_accounts` registry entries. Nothing in a run depends on them surviving, because
 * every test arranges what it needs, but the last module to run therefore leaves the shared
 * database at a state no migration ever produces. `mvn install` stops being re-runnable without a
 * manual `./malai-db.sh`, and so does any scoped `mvn test -pl <module>`.
 *
 * Clearing the last class's fixtures and re-running the seeding migrations, rather than a Flyway
 * clean + migrate: the latter drops and recreates every object in the schema, which on a
 * bind-mounted data directory cost around six seconds per JVM and roughly two and a half minutes
 * across a full build. The seed rows still come from the migration files themselves — Flyway
 * enumerates them and `V*seed*.sql` is `INSERT … ON CONFLICT DO NOTHING` — so no hand-maintained
 * row list can fall behind. `SharedDatabaseHandBackTest` pins the equivalence to a real
 * clean + migrate, so a future migration that seeds rows this misses turns it red.
 *
 * Only a JVM that actually truncated hands back. The e2e suite calls [deleteAllFixtures] against
 * its own database on port 5433 without going through `MalaiAcceptanceTest`, and must not be reset.
 */
object SharedDatabaseHandBack {

    @Volatile
    private var owesAHandBack = false

    fun recordBorrowing() {
        owesAHandBack = true
    }

    fun handBack() {
        if (!owesAHandBack) return
        owesAHandBack = false

        println("Handing the shared database back at post-migration state")

        MalaiSchema.refuseAnythingButTheTestDatabase(MalaiAcceptanceTestContext.DATABASE_URL)
        DatabaseConnection.createConnection(MalaiAcceptanceTestContext.DATABASE_URL)
            .use { it.deleteAllFixtures() }
        MalaiSchema.restoreSeededRows()
    }
}
