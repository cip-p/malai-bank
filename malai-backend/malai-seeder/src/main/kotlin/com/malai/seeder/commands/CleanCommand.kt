package com.malai.seeder.commands

import com.malai.seeder.cognito.SeederCognitoAdmin
import com.malai.seeder.data.AmlDemoSeed
import com.malai.seeder.data.BankingDaySeed
import com.malai.seeder.data.CustomerOnlySeed
import com.malai.seeder.data.ManagerOnlySeed
import com.malai.seeder.data.OpsReviewerSeed
import com.malai.seeder.data.SarahMitchellSeed
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import picocli.CommandLine.Command

/**
 * Surgical clean: deletes ONLY the rows the seeder wrote for its demo
 * identities — the full customer estates of Sarah Mitchell and the
 * customer-only user, the ops reviewer's and the manager's single `users`
 * rows (back-office staff and the executive have no customer estate), and
 * the banking-day cohort ([BankingDaySeed]):
 * the five demo-day new customers' estates plus every `payments` row the seeder
 * wrote (each new customer's inbound deposit and Sarah's outbound declined
 * payments). Deleting the demo-day payments is what lets `reset` re-anchor them
 * to run time — `insertPayment`'s `ON CONFLICT DO NOTHING` would otherwise keep a
 * stale `created_at` and the Deposits / Payments-stopped tiles would read zero on
 * the next demo day. The banking-day new customers carry no Cognito identity (they
 * are demo statistics, not login personas), so no `AdminDeleteUser` is emitted for
 * them, mirroring the seed's structural exclusion.
 *
 * The dev DB is shared with manual testing and future demo customers, so the
 * earlier TRUNCATE-all-tables behaviour was too coarse — it would have nuked
 * any non-Sarah rows added between re-seeds. Scoping every DELETE through
 * `users.email = <fixture email>` is robust against Cognito sub
 * rotation (`users.id` is now Cognito-generated, see quick 260512-mn7) and
 * naturally cascades through `account_holder.user_id`, `accounts.user_id`,
 * etc. without relying on any DB-level ON DELETE CASCADE.
 *
 * Ledger entries are deleted by `transaction_id IN (the estate's transactions)`,
 * not by `account_id IN (the estate's accounts)`. Sarah's backdated demo postings
 * carry the bank's SCHEME_SETTLEMENT system account as the counterparty leg
 * (V16-seeded, V21 retired the per-merchant rows), so the account-side filter
 * would leave the system-side leg behind and the V5 deferred sum-to-zero
 * trigger would fire at COMMIT. The transaction-id filter catches both legs.
 *
 * Deliberately NOT touched:
 *
 *  - The system user / account_holder + the SUSPENSE accounts they own
 *    (V16/V18/V19/V20 + V14 for the user row) — they're shared across all
 *    customers and the seeder reuses them on the next run.
 *  - `audit_events` — append-only by design. Sarah's previous SEED_* rows
 *    stay in the chain; the next seed run appends new rows after them.
 *  - `mdr_entries` — append-only like `audit_events`, and its `chain_head`
 *    scope (`mdr`) stays put for the same reason. The seeded Model Decisions
 *    use fixed entry ids, so the next seed run replays them idempotently and
 *    appends nothing new — there is nothing to delete and no way to delete it
 *    (the malai_app role has no DELETE grant on the table).
 *  - `chain_head` — its `last_hash`/`last_seq` reflects the actual tail of
 *    `audit_events`. Resetting to genesis while leaving rows behind would
 *    break `AuditChainVerifier` with PrevHashMismatch on the next write.
 *  - `idempotency_keys` — the seeder never writes any, so there's nothing
 *    of Sarah's to remove.
 *
 * Cognito side: `AdminDeleteUser` swallows `UserNotFoundException` so
 * re-running against an already-clean pool is a no-op.
 */
@Component
@Command(name = "clean", description = ["Delete the seeded demo identities' rows. Leaves audit log + chain_head + non-seeded rows intact."])
class CleanCommand(
    private val jdbc: JdbcTemplate,
    private val cognitoAdmin: SeederCognitoAdmin,
) : Runnable {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run() {
        log.info("clean: deleting Sarah's demo rows (audit log + chain_head untouched)")
        deleteCustomerEstate(SarahMitchellSeed.EMAIL)

        log.info("clean: deleting the customer-only user's demo rows")
        deleteCustomerEstate(CustomerOnlySeed.EMAIL)

        log.info("clean: deleting ops reviewer row")
        deleteAndLog("users", "DELETE FROM users WHERE email = ?", OpsReviewerSeed.EMAIL)

        log.info("clean: deleting manager row")
        deleteAndLog("users", "DELETE FROM users WHERE email = ?", ManagerOnlySeed.EMAIL)

        log.info("clean: deleting the banking-day new-customer cohort")
        BankingDaySeed.NEW_CUSTOMERS.forEach { customer -> deleteCustomerEstate(customer.email) }

        log.info("clean: deleting the AML monitored customers and their cases")
        AmlDemoSeed.MONITORED_CUSTOMERS.forEach { customer -> deleteCustomerEstate(customer.email) }

        log.info("clean: deleting Cognito demo users")
        cognitoAdmin.adminDeleteUser(SarahMitchellSeed.EMAIL)
        cognitoAdmin.adminDeleteUser(CustomerOnlySeed.EMAIL)
        cognitoAdmin.adminDeleteUser(OpsReviewerSeed.EMAIL)
        cognitoAdmin.adminDeleteUser(ManagerOnlySeed.EMAIL)
        AmlDemoSeed.MONITORED_CUSTOMERS.forEach { customer -> cognitoAdmin.adminDeleteUser(customer.email) }
    }

    // FK leaf → root. Each subselect re-derives the user / account_holder /
    // account ids from the stable email — never trust the in-memory UUID, since
    // the seeded user_id is the Cognito `sub` (rotates if the pool is recreated).
    private fun deleteCustomerEstate(email: String) {
        // Cases first: `aml_cases` holds MDR decision ids as opaque refs, never foreign keys, so
        // nothing cascades here — and deleting them is what lets a re-seed rebuild the museum (and
        // clears any case a presenter live-fired onto a seeded account) while `mdr_entries` keeps
        // the alerts and the SAR themselves, append-only, to be replayed by their fixed entry ids.
        deleteAndLog(
            "aml_cases",
            """
            DELETE FROM aml_cases
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "payments",
            """
            DELETE FROM payments
             WHERE created_by_account_holder_id IN (
               SELECT account_holder_id FROM account_holders
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "ledger_entries",
            """
            DELETE FROM ledger_entries
             WHERE transaction_id IN (
               SELECT transaction_id FROM transactions
                WHERE initiated_by_id IN (
                  SELECT account_holder_id FROM account_holders
                   WHERE user_id IN (SELECT id FROM users WHERE email = ?)
                )
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "transactions",
            """
            DELETE FROM transactions
             WHERE initiated_by_id IN (
               SELECT account_holder_id FROM account_holders
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "direct_debits",
            """
            DELETE FROM direct_debits
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "standing_orders",
            """
            DELETE FROM standing_orders
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "payees",
            """
            DELETE FROM payees
             WHERE account_holder_id IN (
               SELECT account_holder_id FROM account_holders
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "card_refunds",
            """
            DELETE FROM card_refunds
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "card_authorisations",
            """
            DELETE FROM card_authorisations
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "holds",
            """
            DELETE FROM holds
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "cards",
            """
            DELETE FROM cards
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "account_identifiers",
            """
            DELETE FROM account_identifiers
             WHERE account_id IN (
               SELECT account_id FROM accounts
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "accounts",
            "DELETE FROM accounts WHERE user_id IN (SELECT id FROM users WHERE email = ?)",
            email,
        )

        deleteAndLog(
            "individuals",
            """
            DELETE FROM individuals
             WHERE account_holder_id IN (
               SELECT account_holder_id FROM account_holders
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "account_holder_addresses",
            """
            DELETE FROM account_holder_addresses
             WHERE account_holder_id IN (
               SELECT account_holder_id FROM account_holders
                WHERE user_id IN (SELECT id FROM users WHERE email = ?)
             )
            """.trimIndent(),
            email,
        )

        deleteAndLog(
            "account_holders",
            "DELETE FROM account_holders WHERE user_id IN (SELECT id FROM users WHERE email = ?)",
            email,
        )

        deleteAndLog("users", "DELETE FROM users WHERE email = ?", email)
    }

    private fun deleteAndLog(table: String, sql: String, email: String) {
        val deleted = jdbc.update(sql, email)
        log.info("clean: {} deleted={}", table, deleted)
    }
}
