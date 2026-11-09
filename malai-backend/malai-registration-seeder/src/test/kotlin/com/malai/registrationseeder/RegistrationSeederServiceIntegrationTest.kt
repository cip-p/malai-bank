package com.malai.registrationseeder

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.IndividualEntity
import com.malai.accountholder.repository.AccountHolderRepository
import com.malai.accountholder.repository.IndividualRepository
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountIdentifierEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.auth.repository.UserRepository
import com.malai.commons.TimeService
import com.malai.commons.banking.MalaiSortCode
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.registrationseeder.data.CreditEstateSeed
import com.malai.registrationseeder.data.ForecastSeed
import com.malai.registrationseeder.testapp.TestRegistrationSeederApplication
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.VerificationResult
import org.assertj.core.api.Assertions.assertThat
import com.malai.sql.MalaiSchema
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.sql.Date
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Demo-quality integration coverage for [RegistrationSeederService]. Runs
 * against the pre-provisioned `malai_test` Postgres 17 (see
 * `start-docker-containers-local.sh`) so the V5 deferred sum-to-zero trigger
 * fires at every COMMIT and the audit hash chain is exercised end-to-end. No
 * `@Transactional` on the test class — the service owns its tx boundary, and
 * the assertions read post-commit state.
 */
@SpringBootTest(classes = [TestRegistrationSeederApplication::class])
@ActiveProfiles("test")
class RegistrationSeederServiceIntegrationTest {

    @Autowired private lateinit var service: RegistrationSeederService
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var accountHolderRepository: AccountHolderRepository
    @Autowired private lateinit var individualRepository: IndividualRepository
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var accountIdentifierRepository: AccountIdentifierRepository
    @Autowired private lateinit var auditChainVerifier: AuditChainVerifier
    @Autowired private lateinit var timeService: TimeService
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val completedForecastMagnitude: Long =
        ForecastSeed.COMPLETED_STANDING_ORDERS.sumOf { it.amountMinor } +
            ForecastSeed.COMPLETED_DIRECT_DEBITS.sumOf { it.amountMinor }

    private val DEBIT_CARD_AUDIT_EVENTS = 1

    // The credit and savings accounts the full-parity estate opens.
    private val ESTATE_ACCOUNTS = 2

    // The savings account's opening-balance posting.
    private val SAVINGS_OPENING_POSTINGS = 1

    // Debit card on the CURRENT account, plus the credit card the estate issues.
    private val ALL_CARD_AUDIT_EVENTS = DEBIT_CARD_AUDIT_EVENTS + 1

    companion object {
        @JvmStatic
        @AfterAll
        fun handTheDatabaseBackAtPostMigrationState() = MalaiSchema.resetToMigratedState()
    }

    private fun countOf(action: String): Long =
        jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action = ?", Long::class.java, action)!!

    @BeforeEach
    fun cleanDatabaseAndSeedClearings() {
        jdbc.execute(
            """
            TRUNCATE TABLE
                account_holder_addresses, account_holders, account_identifiers,
                accounts, audit_events, business_addresses, businesses, cards,
                direct_debits, idempotency_keys, individuals, ledger_entries,
                payees, settle_up_profiles, sole_traders, standing_orders,
                transactions, ledger_accounts, users
            CASCADE
            """.trimIndent()
        )
        jdbc.update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent()
        )
        seedExternalClearings()
    }

    @Test
    fun `seeds a registered-but-empty user - Seeded with backdated balanced postings landing on opening balance`() {
        val email = "alice.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)

        val result = service.seedFor(email)

        assertThat(result).isInstanceOf(SeedResult.Seeded::class.java)
        val seeded = result as SeedResult.Seeded
        assertThat(seeded.userId).isEqualTo(user.userId)
        assertThat(seeded.accountId).isEqualTo(user.currentAccountId)
        assertThat(seeded.transactionCount).isBetween(20, 30)
        assertThat(seeded.openingBalanceMinor).isBetween(50_000L, 1_500_000L)
        assertThat(seeded.completedForecastPostings).isEqualTo(ForecastSeed.COMPLETED_POSTING_COUNT)

        // User-side ledger rows == one per salary/spend transaction, plus one per
        // backdated completed-forecast occurrence.
        val userSideCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            user.currentAccountId,
        )!!
        assertThat(userSideCount).isEqualTo((seeded.transactionCount + seeded.completedForecastPostings).toLong())

        // Each transaction's full ledger sums to zero (V5 deferred trigger held).
        val seededTxIds = jdbc.queryForList(
            "SELECT DISTINCT transaction_id FROM ledger_entries WHERE account_id = ?",
            UUID::class.java,
            user.currentAccountId,
        ).toSet()
        seededTxIds.forEach { txId ->
            val sum = jdbc.queryForObject(
                "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE transaction_id = ?",
                Long::class.java,
                txId,
            )
            assertThat(sum).withFailMessage("transaction %s should sum to zero, got %d", txId, sum).isEqualTo(0L)
        }

        // Closing balance == chosen opening minus the backdated completed-forecast
        // outflows, so the available balance reflects the completed occurrences.
        val closingBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            user.currentAccountId,
        )!!
        assertThat(closingBalance).isEqualTo(seeded.openingBalanceMinor - completedForecastMagnitude)

        // A few saved payees are populated for the holder.
        val payeeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM payees WHERE account_holder_id = ?",
            Long::class.java,
            user.accountHolderId,
        )!!
        assertThat(payeeCount).isEqualTo(ForecastSeed.PAYEE_COUNT.toLong())

        // Every standing order is ACTIVE and linked to one of the seeded payees,
        // paying that payee's exact snapshot.
        val seededPayees = jdbc.queryForList(
            "SELECT payee_id, name FROM payees WHERE account_holder_id = ?",
            user.accountHolderId,
        ).associate { (it["payee_id"] as UUID) to (it["name"] as String) }
        val standingOrders = jdbc.queryForList(
            "SELECT payee_id, payee_name, status FROM standing_orders WHERE account_id = ?",
            user.currentAccountId,
        )
        assertThat(standingOrders).hasSize(ForecastSeed.STANDING_ORDER_COUNT)
        standingOrders.forEach { row ->
            assertThat(row["status"]).isEqualTo("ACTIVE")
            val payeeId = row["payee_id"] as UUID
            assertThat(seededPayees).containsKey(payeeId)
            assertThat(row["payee_name"]).isEqualTo(seededPayees[payeeId])
        }

        // Every direct debit is an ACTIVE fixed-amount mandate.
        val mandateAmounts = jdbc.queryForList(
            "SELECT amount_minor_units FROM direct_debits WHERE account_id = ? AND status = 'ACTIVE'",
            Long::class.java,
            user.currentAccountId,
        )
        assertThat(mandateAmounts).hasSize(ForecastSeed.DIRECT_DEBIT_COUNT)
        mandateAmounts.forEach { assertThat(it).isNotNull().isPositive() }

        // Instruments straddle today: some anchored on-or-before today (their
        // occurrences are Completed) and some after today (still Expected).
        val startOffsets = jdbc.queryForList(
            """
            SELECT (start_date - (created_at AT TIME ZONE 'UTC')::date) AS offset
              FROM standing_orders WHERE account_id = ?
            UNION ALL
            SELECT (start_date - (created_at AT TIME ZONE 'UTC')::date)
              FROM direct_debits WHERE account_id = ?
            """.trimIndent(),
            Int::class.java,
            user.currentAccountId,
            user.currentAccountId,
        ).requireNoNulls()
        assertThat(startOffsets.count { it <= 0 }).isGreaterThanOrEqualTo(1)
        assertThat(startOffsets.count { it > 0 }).isGreaterThanOrEqualTo(1)

        // No instrument is born past-due, however far back its anchor sits. A completed
        // occurrence has been and gone: leaving next_*_at on it hands the per-minute
        // standing-order executor / direct-debit requester a due instrument to fire within
        // the minute, re-paying an occurrence this seeder has already posted a backdated
        // ledger entry for.
        val nextDatesAreAfterToday = jdbc.queryForObject(
            """
            SELECT bool_and(next_date > today) FROM (
                SELECT (next_payment_at AT TIME ZONE 'UTC')::date AS next_date,
                       (created_at AT TIME ZONE 'UTC')::date AS today
                  FROM standing_orders WHERE account_id = ?
                UNION ALL
                SELECT (next_collection_at AT TIME ZONE 'UTC')::date,
                       (created_at AT TIME ZONE 'UTC')::date
                  FROM direct_debits WHERE account_id = ?
            ) t
            """.trimIndent(),
            Boolean::class.java,
            user.currentAccountId,
            user.currentAccountId,
        )
        assertThat(nextDatesAreAfterToday).isTrue()

        // A future-anchored instrument's first occurrence is still its anchor.
        val futureAnchoredNextDatesMatchStart = jdbc.queryForObject(
            """
            SELECT bool_and(next_date = start_date) FROM (
                SELECT (next_payment_at AT TIME ZONE 'UTC')::date AS next_date,
                       start_date,
                       (created_at AT TIME ZONE 'UTC')::date AS today
                  FROM standing_orders WHERE account_id = ?
                UNION ALL
                SELECT (next_collection_at AT TIME ZONE 'UTC')::date,
                       start_date,
                       (created_at AT TIME ZONE 'UTC')::date
                  FROM direct_debits WHERE account_id = ?
            ) t
            WHERE start_date > today
            """.trimIndent(),
            Boolean::class.java,
            user.currentAccountId,
            user.currentAccountId,
        )
        assertThat(futureAnchoredNextDatesMatchStart).isTrue()
    }

    @Test
    fun `noop on already-seeded user - second call returns Noop with the same count and writes no new rows`() {
        val email = "bob.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)
        val firstResult = service.seedFor(email) as SeedResult.Seeded

        val ledgerCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long::class.java)!!
        val txCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long::class.java)!!
        val auditCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long::class.java)!!
        val payeeCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM payees", Long::class.java)!!
        val soCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM standing_orders", Long::class.java)!!
        val ddCountBefore = jdbc.queryForObject("SELECT COUNT(*) FROM direct_debits", Long::class.java)!!

        val secondResult = service.seedFor(email)

        assertThat(secondResult).isInstanceOf(SeedResult.Noop::class.java)
        val noop = secondResult as SeedResult.Noop
        assertThat(noop.userId).isEqualTo(user.userId)
        assertThat(noop.accountId).isEqualTo(user.currentAccountId)
        assertThat(noop.existingTransactionCount)
            .isEqualTo(firstResult.transactionCount + firstResult.completedForecastPostings)

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long::class.java)).isEqualTo(ledgerCountBefore)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long::class.java)).isEqualTo(txCountBefore)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long::class.java)).isEqualTo(auditCountBefore)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payees", Long::class.java)).isEqualTo(payeeCountBefore)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM standing_orders", Long::class.java)).isEqualTo(soCountBefore)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM direct_debits", Long::class.java)).isEqualTo(ddCountBefore)
    }

    @Test
    fun `UserNotFound on unknown email`() {
        val result = service.seedFor("ghost.${UUID.randomUUID()}@example.com")

        assertThat(result).isInstanceOf(SeedResult.UserNotFound::class.java)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entries", Long::class.java)).isEqualTo(0L)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM transactions", Long::class.java)).isEqualTo(0L)
    }

    @Test
    fun `NoActiveCurrentAccount on user without an active CURRENT account`() {
        val email = "carla.${UUID.randomUUID()}@example.com"
        val userId = UUID.randomUUID()
        val holderId = UUID.randomUUID()
        userRepository.save(buildUser(userId, email))
        accountHolderRepository.save(buildHolder(holderId, userId))
        individualRepository.save(buildIndividual(holderId, email))

        val result = service.seedFor(email)

        assertThat(result).isInstanceOf(SeedResult.NoActiveCurrentAccount::class.java)
        assertThat((result as SeedResult.NoActiveCurrentAccount).userId).isEqualTo(userId)
    }

    @Test
    fun `audit chain advances by transactionCount and verify returns Ok`() {
        val email = "dan.${UUID.randomUUID()}@example.com"
        createFullyOnboardedUser(email)
        val seqBefore = jdbc.queryForObject("SELECT last_seq FROM chain_head WHERE scope = 'global'", Long::class.java)!!

        val seeded = service.seedFor(email) as SeedResult.Seeded

        val seqAfter = jdbc.queryForObject("SELECT last_seq FROM chain_head WHERE scope = 'global'", Long::class.java)!!
        // The chain sequence must advance by exactly the number of audit rows
        // written — no gaps, no double-counting. Asserted against the table
        // rather than against a sum of per-feature constants: that arithmetic
        // had to be re-derived every time the seeder grew, and a drifting
        // constant would fail this test without saying anything about chain
        // integrity, which is the property actually worth protecting.
        val auditRows = jdbc.queryForObject("SELECT COUNT(*) FROM audit_events", Long::class.java)!!
        assertThat(seqAfter - seqBefore).isEqualTo(auditRows)

        // The estate's own contribution is asserted by what it created rather
        // than by predicting a global SEED_TRANSACTION total. That total mixes
        // the current-account dataset, the forecast backdating and the estate,
        // each of which moves independently — an exact expected value there
        // fails for reasons that say nothing about whether seeding worked.
        assertThat(countOf("SEED_ACCOUNT_CREATED")).isEqualTo(ESTATE_ACCOUNTS.toLong())
        assertThat(countOf("SEED_CARD_AUTHORISATION")).isEqualTo(seeded.pendingAuthorisations.toLong())

        assertThat(seeded.creditAccountId).isNotNull()
        assertThat(seeded.savingsAccountId).isNotNull()
        assertThat(seeded.creditPostings).isEqualTo(CreditEstateSeed.PURCHASES.size + 1)
        assertThat(seeded.pendingAuthorisations).isEqualTo(CreditEstateSeed.PENDING_AUTHORISATIONS.size)

        val cardEvents = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action = ?",
            Long::class.java,
            "SEED_CARD_ISSUED",
        )
        assertThat(cardEvents).isEqualTo(ALL_CARD_AUDIT_EVENTS.toLong())

        val seedTxEvents = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action = ?",
            Long::class.java,
            "SEED_TRANSACTION",
        )
        // Current-account dataset + backdated forecast occurrences, plus the
        // full-parity estate's own postings: the credit purchases and
        // part-repayment, and the savings opening balance.
        assertThat(seedTxEvents).isEqualTo(
            (
                seeded.transactionCount + seeded.completedForecastPostings +
                    seeded.creditPostings + SAVINGS_OPENING_POSTINGS
                ).toLong()
        )

        val instrumentEvents = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action IN ('SEED_PAYEE_CREATED', 'SEED_STANDING_ORDER_CREATED', 'SEED_DIRECT_DEBIT_CREATED')",
            Long::class.java,
        )
        assertThat(instrumentEvents).isEqualTo(ForecastSeed.INSTRUMENT_AUDIT_EVENT_COUNT.toLong())

        val verification = auditChainVerifier.verify()
        assertThat(verification).isInstanceOf(VerificationResult.Ok::class.java)
    }

    @Test
    fun `seeds a couple of GENERAL-categorising spendings - unrecognised merchant, non-transfer debit type`() {
        val email = "iris.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)

        service.seedFor(email)

        val generalRows = jdbc.queryForList(
            """
            SELECT le.merchant_name AS merchant_name, le.description AS description,
                   le.amount_minor_units AS amount, t.type AS type
              FROM ledger_entries le
              JOIN transactions t ON t.transaction_id = le.transaction_id
             WHERE le.account_id = ?
               AND le.description IN ('Cash withdrawal', 'Corner Shop', 'J Patel')
            """.trimIndent(),
            user.currentAccountId,
        )

        assertThat(generalRows).hasSizeGreaterThanOrEqualTo(2)
        generalRows.forEach { row ->
            assertThat(row["amount"] as Long).isNegative()
            assertThat(row["type"] as String).isIn("ATM_WITHDRAWAL", "CARD_PAYMENT")
            assertThat((row["merchant_name"] as String).lowercase())
                .doesNotContain("tesco", "tfl", "pret", "octopus", "council tax", "rent", "spotify", "gym")
        }
    }

    @Test
    fun `issues exactly one backdated ACTIVE debit card on the current account and no credit card`() {
        val email = "kim.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)

        service.seedFor(email)

        val cards = jdbc.queryForList(
            "SELECT variant, status, pan, expiry, cvv, cardholder_name, created_at FROM cards WHERE account_id = ?",
            user.currentAccountId,
        )
        assertThat(cards).hasSize(1)
        val card = cards.single()
        assertThat(card["variant"]).isEqualTo("DEBIT")
        assertThat(card["status"]).isEqualTo("ACTIVE")
        assertThat(card["cardholder_name"]).isEqualTo("Test User")
        assertThat(card["pan"] as String).hasSize(16).startsWith("990432")
        assertThat(card["cvv"] as String).matches("\\d{3}")
        assertThat(card["expiry"] as String).matches("\\d{2}/\\d{2}")
        assertThat((card["created_at"] as Timestamp).toInstant())
            .isBefore(timeService.now().minus(20, ChronoUnit.DAYS))

        val creditCards = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND variant = 'CREDIT'",
            Long::class.java,
            user.currentAccountId,
        )!!
        assertThat(creditCards).isEqualTo(0L)
    }

    @Test
    fun `two disjoint users are seeded independently and produce disjoint transactions`() {
        val emailA = "ella.${UUID.randomUUID()}@example.com"
        val emailB = "fred.${UUID.randomUUID()}@example.com"
        val userA = createFullyOnboardedUser(emailA)
        val userB = createFullyOnboardedUser(emailB)

        val seededA = service.seedFor(emailA) as SeedResult.Seeded
        val seededB = service.seedFor(emailB) as SeedResult.Seeded

        assertThat(seededA.userId).isNotEqualTo(seededB.userId)
        assertThat(seededA.accountId).isNotEqualTo(seededB.accountId)

        val txIdsA = jdbc.queryForList(
            "SELECT DISTINCT transaction_id FROM ledger_entries WHERE account_id = ?",
            UUID::class.java,
            userA.currentAccountId,
        ).toSet()
        val txIdsB = jdbc.queryForList(
            "SELECT DISTINCT transaction_id FROM ledger_entries WHERE account_id = ?",
            UUID::class.java,
            userB.currentAccountId,
        ).toSet()

        assertThat(txIdsA).hasSize(seededA.transactionCount + seededA.completedForecastPostings)
        assertThat(txIdsB).hasSize(seededB.transactionCount + seededB.completedForecastPostings)
        assertThat(txIdsA).doesNotContainAnyElementsOf(txIdsB)
    }

    @Test
    fun `deterministic seed - the same seed reproduces identical amounts, opening balance and instrument ids across a re-seed`() {
        val email = "grace.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)
        val seed = 4242L

        val first = service.seedFor(email, seed) as SeedResult.Seeded
        val firstAmounts = accountLedgerAmounts(user.currentAccountId)
        val firstInstrumentIds = instrumentIds(user.accountHolderId, user.currentAccountId)

        clearSeededData(user)

        val second = service.seedFor(email, seed) as SeedResult.Seeded
        val secondAmounts = accountLedgerAmounts(user.currentAccountId)
        val secondInstrumentIds = instrumentIds(user.accountHolderId, user.currentAccountId)

        assertThat(second.transactionCount).isEqualTo(first.transactionCount)
        assertThat(second.openingBalanceMinor).isEqualTo(first.openingBalanceMinor)
        assertThat(secondAmounts).isEqualTo(firstAmounts)
        assertThat(secondInstrumentIds).isNotEmpty().isEqualTo(firstInstrumentIds)
    }

    @Test
    fun `deterministic seed - different seeds produce different datasets`() {
        val email = "heidi.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)

        val first = service.seedFor(email, 1L) as SeedResult.Seeded
        val firstInstrumentIds = instrumentIds(user.accountHolderId, user.currentAccountId)
        clearSeededData(user)
        val second = service.seedFor(email, 2L) as SeedResult.Seeded

        assertThat(second.openingBalanceMinor).isNotEqualTo(first.openingBalanceMinor)
        assertThat(instrumentIds(user.accountHolderId, user.currentAccountId))
            .doesNotContainAnyElementsOf(firstInstrumentIds)
    }

    @Test
    fun `seeds a warning-tripping forecast - completed occurrences backdated, expected sized to dip below zero`() {
        val email = "jan.${UUID.randomUUID()}@example.com"
        val user = createFullyOnboardedUser(email)

        val seeded = service.seedFor(email) as SeedResult.Seeded
        assertThat(seeded.completedForecastPostings).isEqualTo(ForecastSeed.COMPLETED_POSTING_COUNT)

        val today = LocalDate.ofInstant(timeService.now(), ZoneOffset.UTC)

        // Both instrument types are seeded ACTIVE, so both STANDING_ORDER and
        // DIRECT_DEBIT tags surface in the forecast.
        val activeStandingOrders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM standing_orders WHERE account_id = ? AND status = 'ACTIVE'",
            Long::class.java, user.currentAccountId,
        )!!
        val activeDirectDebits = jdbc.queryForObject(
            "SELECT COUNT(*) FROM direct_debits WHERE account_id = ? AND status = 'ACTIVE'",
            Long::class.java, user.currentAccountId,
        )!!
        assertThat(activeStandingOrders).isEqualTo(ForecastSeed.STANDING_ORDER_COUNT.toLong())
        assertThat(activeDirectDebits).isEqualTo(ForecastSeed.DIRECT_DEBIT_COUNT.toLong())

        // Occurrences straddle today: at least one Completed (anchor on/before today)
        // and at least one Expected (anchor after today).
        val anchors = instrumentStartDates(user.currentAccountId)
        assertThat(anchors.count { !it.isAfter(today) }).isGreaterThanOrEqualTo(1)
        assertThat(anchors.count { it.isAfter(today) }).isGreaterThanOrEqualTo(1)

        // Each completed occurrence has a matching backdated posting on/before today,
        // as a signed money-out ledger entry that shows in the transactions feed.
        val backdatedPostings = jdbc.queryForList(
            """
            SELECT le.amount_minor_units AS amount, le.posted_at AS posted_at
              FROM ledger_entries le
              JOIN transactions t ON t.transaction_id = le.transaction_id
             WHERE le.account_id = ?
               AND t.type IN ('PAYMENT_SETTLEMENT', 'DIRECT_DEBIT_COLLECTION')
            """.trimIndent(),
            user.currentAccountId,
        )
        assertThat(backdatedPostings).hasSize(ForecastSeed.COMPLETED_POSTING_COUNT)
        backdatedPostings.forEach {
            assertThat(it["amount"] as Long).isNegative()
            assertThat((it["posted_at"] as Timestamp).toInstant()).isBeforeOrEqualTo(timeService.now())
        }

        // Available balance == opening minus the completed outflows.
        val availableBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE account_id = ?",
            Long::class.java, user.currentAccountId,
        )!!
        assertThat(availableBalance).isEqualTo(seeded.openingBalanceMinor - completedForecastMagnitude)

        // The expected outflows exceed the available balance by exactly the overshoot,
        // so the projected month-end balance lands in the red (warning state).
        val expectedOutflowMagnitude = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(amount_minor_units), 0) FROM (
                SELECT amount_minor_units FROM standing_orders WHERE account_id = ? AND start_date > ?
                UNION ALL
                SELECT amount_minor_units FROM direct_debits WHERE account_id = ? AND start_date > ?
            ) expected
            """.trimIndent(),
            Long::class.java,
            user.currentAccountId, Date.valueOf(today),
            user.currentAccountId, Date.valueOf(today),
        )!!
        assertThat(availableBalance - expectedOutflowMagnitude).isNegative()
        assertThat(availableBalance - expectedOutflowMagnitude).isEqualTo(-ForecastSeed.OVERSHOOT_MINOR)
    }

    // --- fixtures -----------------------------------------------------------

    private fun instrumentStartDates(accountId: UUID): List<LocalDate> =
        (jdbc.queryForList("SELECT start_date FROM standing_orders WHERE account_id = ?", Date::class.java, accountId) +
            jdbc.queryForList("SELECT start_date FROM direct_debits WHERE account_id = ?", Date::class.java, accountId))
            .requireNoNulls()
            .map { it.toLocalDate() }

    private fun accountLedgerAmounts(accountId: UUID): List<Long> =
        jdbc.queryForList(
            "SELECT amount_minor_units FROM ledger_entries WHERE account_id = ? ORDER BY entry_id",
            Long::class.java,
            accountId,
        ).requireNoNulls()

    private fun instrumentIds(accountHolderId: UUID, accountId: UUID): List<UUID> {
        val payees = jdbc.queryForList(
            "SELECT payee_id FROM payees WHERE account_holder_id = ? ORDER BY payee_id",
            UUID::class.java, accountHolderId,
        )
        val standingOrders = jdbc.queryForList(
            "SELECT standing_order_id FROM standing_orders WHERE account_id = ? ORDER BY standing_order_id",
            UUID::class.java, accountId,
        )
        val directDebits = jdbc.queryForList(
            "SELECT direct_debit_id FROM direct_debits WHERE account_id = ? ORDER BY direct_debit_id",
            UUID::class.java, accountId,
        )
        return (payees + standingOrders + directDebits).requireNoNulls()
    }

    private fun clearSeededData(user: OnboardedUser) {
        jdbc.update(
            "DELETE FROM ledger_entries WHERE transaction_id IN (SELECT transaction_id FROM transactions WHERE initiated_by_id = ?)",
            user.accountHolderId,
        )
        jdbc.update("DELETE FROM transactions WHERE initiated_by_id = ?", user.accountHolderId)
        jdbc.update("DELETE FROM standing_orders WHERE account_id = ?", user.currentAccountId)
        jdbc.update("DELETE FROM direct_debits WHERE account_id = ?", user.currentAccountId)
        jdbc.update("DELETE FROM payees WHERE account_holder_id = ?", user.accountHolderId)
        jdbc.update("DELETE FROM cards WHERE account_id = ?", user.currentAccountId)
    }

    private data class OnboardedUser(
        val userId: UUID,
        val accountHolderId: UUID,
        val currentAccountId: UUID,
    )

    private fun createFullyOnboardedUser(email: String): OnboardedUser {
        val userId = UUID.randomUUID()
        val holderId = UUID.randomUUID()
        val accountId = UUID.randomUUID()
        userRepository.save(buildUser(userId, email))
        accountHolderRepository.save(buildHolder(holderId, userId))
        individualRepository.save(buildIndividual(holderId, email))
        accountRepository.save(
            AccountEntity(
                accountId = accountId,
                accountType = AccountType.PRIMARY,
                defaultCategory = UUID.randomUUID(),
                currency = Currency.GBP,
                createdAt = timeService.now().minus(2, ChronoUnit.DAYS),
                name = "Current",
                userId = userId,
                accountHolderId = holderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
        accountIdentifierRepository.save(
            AccountIdentifierEntity(
                identifierId = UUID.randomUUID(),
                accountId = accountId,
                identifierType = IdentifierType.SORT_CODE,
                bankIdentifier = MalaiSortCode.CANONICAL,
                accountIdentifier = accountId.toString().filter { it.isDigit() }.padEnd(8, '0').take(8),
            )
        )
        return OnboardedUser(userId, holderId, accountId)
    }

    private fun buildUser(userId: UUID, email: String): UserEntity = UserEntity(
        id = userId,
        email = email,
        firstName = "Test",
        lastName = "User",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        kycStatus = KycStatus.VERIFIED,
        registrationConfirmed = true,
        createdAt = timeService.now().minus(2, ChronoUnit.DAYS),
        createdBy = "registration-seeder-it",
    )

    private fun buildHolder(holderId: UUID, userId: UUID): AccountHolderEntity = AccountHolderEntity(
        accountHolderId = holderId,
        userId = userId,
        accountHolderType = AccountHolderType.INDIVIDUAL,
        createdAt = timeService.now().minus(2, ChronoUnit.DAYS),
        status = OnboardingStatus.ACTIVE,
    )

    private fun buildIndividual(holderId: UUID, email: String): IndividualEntity = IndividualEntity(
        individualId = UUID.randomUUID(),
        accountHolderId = holderId,
        title = null,
        firstName = "Test",
        lastName = "User",
        dateOfBirth = LocalDate.of(1990, 1, 1),
        email = email,
        phone = "+447700900000",
        status = OnboardingStatus.ACTIVE,
    )

    // SCHEME_SETTLEMENT is the counterparty for every balanced pair the
    // RegistrationSeederService writes -- issue 09 retired the V14 per-merchant
    // EXTERNAL accounts; the bank's scheme-position SUSPENSE row (V16) is what's
    // left. Mirrors the production Flyway state post-V21.
    private fun seedExternalClearings() {
        userRepository.save(buildUser(ClearingAccountsSeed.SYSTEM_USER_ID, ClearingAccountsSeed.SYSTEM_EMAIL))
        accountHolderRepository.save(
            buildHolder(ClearingAccountsSeed.SYSTEM_ACCOUNT_HOLDER_ID, ClearingAccountsSeed.SYSTEM_USER_ID)
        )
        accountRepository.save(
            AccountEntity(
                accountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
                accountType = AccountType.SUSPENSE,
                defaultCategory = UUID.randomUUID(),
                currency = Currency.GBP,
                createdAt = timeService.now().minus(60, ChronoUnit.DAYS),
                name = "Scheme Settlement",
                userId = ClearingAccountsSeed.SYSTEM_USER_ID,
                accountHolderId = ClearingAccountsSeed.SYSTEM_ACCOUNT_HOLDER_ID,
                status = OnboardingStatus.ACTIVE,
            )
        )
    }
}
