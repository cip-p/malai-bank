package com.malai.seeder.commands

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.repository.AccountHolderRepository
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.repository.AccountRepository
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.auth.repository.UserRepository
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.commons.TimeService
import com.malai.mdr.service.MdrChainVerifier
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.seeder.cognito.SeederCognitoAdmin
import com.malai.seeder.data.AmlDemoSeed
import com.malai.seeder.data.BankingDaySeed
import com.malai.seeder.data.CustomerOnlySeed
import com.malai.seeder.data.ManagerOnlySeed
import com.malai.seeder.data.MdrDemoSeed
import com.malai.seeder.data.OpsReviewerSeed
import com.malai.seeder.data.SarahMitchellSeed
import com.malai.seeder.testapp.TestSeederApplication
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.VerificationResult
import org.assertj.core.api.Assertions.assertThat
import com.malai.sql.MalaiSchema
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.sql.Timestamp
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Demo-quality integration coverage for [SeedCommand]'s flagship credit-card estate and PAN
 * registration (cards issue 12). Runs against the pre-provisioned `malai_test` Postgres (see
 * `start-docker-containers-local.sh`) so the V5 deferred sum-to-zero trigger fires at COMMIT and
 * the audit hash chain is exercised end-to-end.
 *
 * The stub client is a Mockito bean: SeedCommand's PAN registration is asserted at the outgoing-call
 * boundary (the HTTP wiring itself is covered by MalaiStubClientTest), and no live stub is required.
 * Cognito is a Mockito bean for the same reason: `adminCreateUser` returns null (as the real bean
 * does when the pool is disabled), so `run()` binds the static SarahMitchellSeed.USER_ID /
 * OpsReviewerSeed.USER_ID fallbacks, and the group-assignment calls are asserted at the boundary.
 */
@SpringBootTest(classes = [TestSeederApplication::class])
@ActiveProfiles("test")
class SeedCommandIntegrationTest {

    @Autowired private lateinit var seedCommand: SeedCommand
    @Autowired private lateinit var cleanCommand: CleanCommand
    @Autowired private lateinit var userRepository: UserRepository
    @Autowired private lateinit var accountHolderRepository: AccountHolderRepository
    @Autowired private lateinit var accountRepository: AccountRepository
    @Autowired private lateinit var auditChainVerifier: AuditChainVerifier
    @Autowired private lateinit var mdrChainVerifier: MdrChainVerifier
    @Autowired private lateinit var timeService: TimeService
    @Autowired private lateinit var jdbc: JdbcTemplate

    @MockitoBean private lateinit var malaiStubClient: MalaiStubClient
    @MockitoBean private lateinit var cognitoAdmin: SeederCognitoAdmin

    companion object {
        @JvmStatic
        @AfterAll
        fun handTheDatabaseBackAtPostMigrationState() = MalaiSchema.resetToMigratedState()
    }

    @BeforeEach
    fun cleanDatabaseAndSeedClearings() {
        jdbc.execute(
            """
            TRUNCATE TABLE
                account_holder_addresses, account_holders, account_identifiers, accounts,
                aml_cases, audit_events, business_addresses, businesses, card_authorisations,
                card_refunds, cards, direct_debits, holds, idempotency_keys, individuals,
                ledger_accounts, ledger_entries, mdr_entries, payees, payments,
                settle_up_profiles, sole_traders, standing_orders, transactions, users
            CASCADE
            """.trimIndent()
        )
        listOf("global", "mdr").forEach { scope ->
            jdbc.update(
                """
                INSERT INTO chain_head (scope, last_hash, last_seq)
                VALUES (?, E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
                ON CONFLICT (scope) DO UPDATE
                    SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
                """.trimIndent(),
                scope,
            )
        }
        seedSchemeSettlement()
    }

    @Test
    fun `seeds a flagship CREDIT_CARD account registered FLOORED at the negative credit limit`() {
        seedCommand.run()

        val account = jdbc.queryForMap(
            "SELECT account_type, status, name, created_at, account_holder_id FROM accounts WHERE account_id = ?",
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )
        assertThat(account["account_type"]).isEqualTo("CREDIT_CARD")
        assertThat(account["status"]).isEqualTo("ACTIVE")
        assertThat(account["name"]).isEqualTo("Credit Card")
        assertThat(account["account_holder_id"]).isEqualTo(SarahMitchellSeed.ACCOUNT_HOLDER_ID)
        assertThat((account["created_at"] as Timestamp).toInstant())
            .isBefore(timeService.now().minus(60, ChronoUnit.DAYS))

        val ledgerAccount = jdbc.queryForMap(
            "SELECT guard_policy, floor_minor_units FROM ledger_accounts WHERE account_id = ?",
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )
        assertThat(ledgerAccount["guard_policy"]).isEqualTo("FLOORED")
        assertThat(ledgerAccount["floor_minor_units"]).isEqualTo(-250_000L)

        val identifier = jdbc.queryForMap(
            "SELECT identifier_type, account_identifier FROM account_identifiers WHERE account_id = ?",
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )
        assertThat(identifier["identifier_type"]).isEqualTo("SORT_CODE")
        assertThat(identifier["account_identifier"]).isEqualTo(SarahMitchellSeed.CREDIT_ACCOUNT_NUMBER)
    }

    @Test
    fun `issues an ACTIVE CREDIT card on the credit account with a pinned id and Malai-BIN PAN`() {
        seedCommand.run()

        val card = jdbc.queryForMap(
            "SELECT card_id, variant, status, cardholder_name, pan FROM cards WHERE account_id = ?",
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )
        assertThat(card["card_id"]).isEqualTo(SarahMitchellSeed.CREDIT_CARD_ID)
        assertThat(card["variant"]).isEqualTo("CREDIT")
        assertThat(card["status"]).isEqualTo("ACTIVE")
        assertThat(card["cardholder_name"]).isEqualTo("Sarah Mitchell")
        assertThat(card["pan"] as String).hasSize(16).startsWith("990432")
    }

    @Test
    fun `settled history less a partial repayment leaves owed above 200 pounds - the 2 point 5 percent minimum branch`() {
        seedCommand.run()

        val ledgerBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!
        assertThat(ledgerBalance).isEqualTo(-SarahMitchellSeed.CREDIT_OWED_MINOR)

        val owed = -ledgerBalance
        assertThat(owed).isEqualTo(35_000L)
        assertThat(owed)
            .withFailMessage("owed must exceed £200 (20000 minor) so the 2.5%% branch fires, not the £5 floor")
            .isGreaterThan(20_000L)

        // A repayment credit is present in the history (the "part-paid" statement).
        val creditPostings = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ? AND amount_minor_units > 0",
            Long::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!
        assertThat(creditPostings).isEqualTo(1L)
    }

    @Test
    fun `seeds two AUTHORISED pending authorisations with matching PLACED holds on the current account`() {
        seedCommand.run()

        val debitCardId = jdbc.queryForObject(
            "SELECT card_id FROM cards WHERE account_id = ? AND variant = 'DEBIT'",
            UUID::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )

        val auths = jdbc.queryForList(
            """
            SELECT authorisation_id, card_id, status, merchant_name, mcc, amount_minor_units,
                   currency, hold_id
              FROM card_authorisations
             WHERE account_id = ?
             ORDER BY amount_minor_units DESC
            """.trimIndent(),
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )
        assertThat(auths).hasSize(2)
        auths.forEach { row ->
            assertThat(row["status"]).isEqualTo("AUTHORISED")
            assertThat(row["card_id"]).isEqualTo(debitCardId)
            assertThat(row["currency"]).isEqualTo("GBP")
            assertThat(row["hold_id"]).isEqualTo(row["authorisation_id"])
        }
        assertThat(auths[0]["merchant_name"]).isEqualTo("Harbour Fuel")
        assertThat(auths[0]["amount_minor_units"]).isEqualTo(5_200L)
        assertThat(auths[1]["merchant_name"]).isEqualTo("Nimbus Groceries")
        assertThat(auths[1]["amount_minor_units"]).isEqualTo(1_875L)

        // Each authorisation carries a real PLACED hold on the current account, so the
        // available balance (ledger − Σ PLACED holds) genuinely drops.
        val heldMinorUnits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM holds WHERE account_id = ? AND status = 'PLACED'",
            Long::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(heldMinorUnits).isEqualTo(7_075L)

        val holdIds = jdbc.queryForList(
            "SELECT hold_id FROM holds WHERE account_id = ?",
            UUID::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        ).toSet()
        assertThat(holdIds).containsExactlyInAnyOrder(
            SarahMitchellSeed.PENDING_AUTH_1_ID,
            SarahMitchellSeed.PENDING_AUTH_2_ID,
        )
    }

    @Test
    fun `registers both the debit and credit card PANs with the stub scheme`() {
        seedCommand.run()

        val debitPan = jdbc.queryForObject(
            "SELECT pan FROM cards WHERE account_id = ? AND variant = 'DEBIT'",
            String::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )!!
        val creditPan = jdbc.queryForObject(
            "SELECT pan FROM cards WHERE account_id = ? AND variant = 'CREDIT'",
            String::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!

        verify(malaiStubClient).registerCardPan(debitPan)
        verify(malaiStubClient).registerCardPan(creditPan)
    }

    @Test
    fun `re-seed after clean reproduces the credit estate without a one-active-card constraint violation`() {
        seedCommand.run()
        cleanCommand.run()
        seedCommand.run()

        val creditAccounts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE account_id = ? AND account_type = 'CREDIT_CARD'",
            Long::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!
        assertThat(creditAccounts).isEqualTo(1L)

        val activeCreditCards = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND variant = 'CREDIT' AND status = 'ACTIVE'",
            Long::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!
        assertThat(activeCreditCards).isEqualTo(1L)

        val activeDebitCards = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND variant = 'DEBIT' AND status = 'ACTIVE'",
            Long::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(activeDebitCards).isEqualTo(1L)

        val ledgerBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
        )!!
        assertThat(ledgerBalance).isEqualTo(-SarahMitchellSeed.CREDIT_OWED_MINOR)
    }

    @Test
    fun `seeds the ops reviewer as an admin-group Cognito identity with a users row and no customer estate`() {
        seedCommand.run()

        val opsUser = jdbc.queryForMap("SELECT id, first_name, last_name, registration_confirmed FROM users WHERE email = ?", OpsReviewerSeed.EMAIL)
        assertThat(opsUser["id"]).isEqualTo(OpsReviewerSeed.USER_ID)
        assertThat(opsUser["first_name"]).isEqualTo(OpsReviewerSeed.FIRST_NAME)
        assertThat(opsUser["last_name"]).isEqualTo(OpsReviewerSeed.LAST_NAME)
        assertThat(opsUser["registration_confirmed"]).isEqualTo(true)

        val opsHolders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_holders WHERE user_id = ?",
            Long::class.java,
            OpsReviewerSeed.USER_ID,
        )!!
        assertThat(opsHolders).isZero()
        val opsAccounts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE user_id = ?",
            Long::class.java,
            OpsReviewerSeed.USER_ID,
        )!!
        assertThat(opsAccounts).isZero()

        verify(cognitoAdmin).adminCreateUser(OpsReviewerSeed.EMAIL, OpsReviewerSeed.FULL_NAME)
        verify(cognitoAdmin).adminAddUserToGroup(OpsReviewerSeed.EMAIL, "admin")
    }

    @Test
    fun `seeds Sarah into both customer and admin groups - the demo persona wears every hat`() {
        seedCommand.run()

        verify(cognitoAdmin).adminAddUserToGroup(SarahMitchellSeed.EMAIL, "customer")
        verify(cognitoAdmin).adminAddUserToGroup(SarahMitchellSeed.EMAIL, "admin")
    }

    @Test
    fun `seeds the customer-only user into exactly the customer Cognito group - never admin`() {
        seedCommand.run()

        verify(cognitoAdmin).adminCreateUser(CustomerOnlySeed.EMAIL, CustomerOnlySeed.FULL_NAME)
        verify(cognitoAdmin).adminSetUserPassword(CustomerOnlySeed.EMAIL, CustomerOnlySeed.DEMO_PASSWORD, permanent = true)
        verify(cognitoAdmin, never()).adminAddUserToGroup(CustomerOnlySeed.EMAIL, "admin")

        val groupsForCustomerOnlyUser = mockingDetails(cognitoAdmin).invocations
            .filter { it.method.name == "adminAddUserToGroup" && it.getArgument<String>(0) == CustomerOnlySeed.EMAIL }
            .map { it.getArgument<String>(1) }
        assertThat(groupsForCustomerOnlyUser).containsExactly("customer")
    }

    @Test
    fun `seeds the manager-only user into exactly the manager Cognito group - never admin or customer`() {
        seedCommand.run()

        verify(cognitoAdmin).adminCreateUser(ManagerOnlySeed.EMAIL, ManagerOnlySeed.FULL_NAME)
        verify(cognitoAdmin).adminSetUserPassword(ManagerOnlySeed.EMAIL, ManagerOnlySeed.DEMO_PASSWORD, permanent = true)
        verify(cognitoAdmin, never()).adminAddUserToGroup(ManagerOnlySeed.EMAIL, "admin")
        verify(cognitoAdmin, never()).adminAddUserToGroup(ManagerOnlySeed.EMAIL, "customer")

        val groupsForManagerOnlyUser = mockingDetails(cognitoAdmin).invocations
            .filter { it.method.name == "adminAddUserToGroup" && it.getArgument<String>(0) == ManagerOnlySeed.EMAIL }
            .map { it.getArgument<String>(1) }
        assertThat(groupsForManagerOnlyUser).containsExactly("manager")
    }

    @Test
    fun `seeds the manager as a users row with no customer estate`() {
        seedCommand.run()

        val managerUser = jdbc.queryForMap(
            "SELECT id, first_name, last_name, registration_confirmed FROM users WHERE email = ?",
            ManagerOnlySeed.EMAIL,
        )
        assertThat(managerUser["id"]).isEqualTo(ManagerOnlySeed.USER_ID)
        assertThat(managerUser["first_name"]).isEqualTo(ManagerOnlySeed.FIRST_NAME)
        assertThat(managerUser["last_name"]).isEqualTo(ManagerOnlySeed.LAST_NAME)
        assertThat(managerUser["registration_confirmed"]).isEqualTo(true)

        val managerHolders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_holders WHERE user_id = ?",
            Long::class.java,
            ManagerOnlySeed.USER_ID,
        )!!
        assertThat(managerHolders).isZero()
        val managerAccounts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE user_id = ?",
            Long::class.java,
            ManagerOnlySeed.USER_ID,
        )!!
        assertThat(managerAccounts).isZero()
    }

    @Test
    fun `seeds the customer-only user with a minimal customer estate via the registration path`() {
        seedCommand.run()

        val customerUser = jdbc.queryForMap(
            "SELECT id, first_name, last_name, registration_confirmed FROM users WHERE email = ?",
            CustomerOnlySeed.EMAIL,
        )
        assertThat(customerUser["id"]).isEqualTo(CustomerOnlySeed.USER_ID)
        assertThat(customerUser["first_name"]).isEqualTo(CustomerOnlySeed.FIRST_NAME)
        assertThat(customerUser["last_name"]).isEqualTo(CustomerOnlySeed.LAST_NAME)
        assertThat(customerUser["registration_confirmed"]).isEqualTo(true)

        val holder = jdbc.queryForMap(
            "SELECT account_holder_id, account_holder_type, status FROM account_holders WHERE user_id = ?",
            CustomerOnlySeed.USER_ID,
        )
        assertThat(holder["account_holder_id"]).isEqualTo(CustomerOnlySeed.ACCOUNT_HOLDER_ID)
        assertThat(holder["account_holder_type"]).isEqualTo("INDIVIDUAL")
        assertThat(holder["status"]).isEqualTo("ACTIVE")

        val individual = jdbc.queryForMap(
            "SELECT individual_id, first_name, last_name, email FROM individuals WHERE account_holder_id = ?",
            CustomerOnlySeed.ACCOUNT_HOLDER_ID,
        )
        assertThat(individual["individual_id"]).isEqualTo(CustomerOnlySeed.INDIVIDUAL_ID)
        assertThat(individual["first_name"]).isEqualTo(CustomerOnlySeed.FIRST_NAME)
        assertThat(individual["last_name"]).isEqualTo(CustomerOnlySeed.LAST_NAME)
        assertThat(individual["email"]).isEqualTo(CustomerOnlySeed.EMAIL)

        val addresses = jdbc.queryForObject(
            "SELECT COUNT(*) FROM account_holder_addresses WHERE account_holder_id = ? AND is_current = true",
            Long::class.java,
            CustomerOnlySeed.ACCOUNT_HOLDER_ID,
        )!!
        assertThat(addresses).isEqualTo(1L)

        val accounts = jdbc.queryForList(
            "SELECT account_id, account_type, status FROM accounts WHERE user_id = ?",
            CustomerOnlySeed.USER_ID,
        )
        assertThat(accounts).hasSize(1)
        assertThat(accounts[0]["account_id"]).isEqualTo(CustomerOnlySeed.CURRENT_ACCOUNT_ID)
        assertThat(accounts[0]["account_type"]).isEqualTo("PRIMARY")
        assertThat(accounts[0]["status"]).isEqualTo("ACTIVE")

        val identifier = jdbc.queryForMap(
            "SELECT identifier_type, account_identifier FROM account_identifiers WHERE account_id = ?",
            CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        )
        assertThat(identifier["identifier_type"]).isEqualTo("SORT_CODE")
        assertThat(identifier["account_identifier"]).isEqualTo(CustomerOnlySeed.CURRENT_ACCOUNT_NUMBER)

        val ledgerEntries = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(ledgerEntries).isGreaterThan(0L)

        val activeDebitCards = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND variant = 'DEBIT' AND status = 'ACTIVE'",
            Long::class.java,
            CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(activeDebitCards).isEqualTo(1L)
    }

    @Test
    fun `clean removes the manager row and its Cognito identity leaving system rows untouched`() {
        seedCommand.run()
        cleanCommand.run()

        val managerUsers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Long::class.java,
            ManagerOnlySeed.EMAIL,
        )!!
        assertThat(managerUsers).isZero()

        val systemUsers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Long::class.java,
            ClearingAccountsSeed.SYSTEM_USER_ID,
        )!!
        assertThat(systemUsers).isEqualTo(1L)

        verify(cognitoAdmin).adminDeleteUser(ManagerOnlySeed.EMAIL)
    }

    @Test
    fun `re-seed after clean reproduces the manager users row idempotently`() {
        seedCommand.run()
        cleanCommand.run()
        seedCommand.run()

        val managerUsers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Long::class.java,
            ManagerOnlySeed.EMAIL,
        )!!
        assertThat(managerUsers).isEqualTo(1L)
    }

    @Test
    fun `clean removes the ops reviewer row and its Cognito identity`() {
        seedCommand.run()
        cleanCommand.run()

        val opsUsers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Long::class.java,
            OpsReviewerSeed.EMAIL,
        )!!
        assertThat(opsUsers).isZero()

        verify(cognitoAdmin).adminDeleteUser(OpsReviewerSeed.EMAIL)
    }

    @Test
    fun `clean removes the customer-only user and estate surgically leaving system rows untouched`() {
        seedCommand.run()
        cleanCommand.run()

        listOf("users" to "email", "individuals" to "email").forEach { (table, column) ->
            val remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM $table WHERE $column = ?",
                Long::class.java,
                CustomerOnlySeed.EMAIL,
            )!!
            assertThat(remaining).describedAs(table).isZero()
        }
        listOf(
            "account_holders" to CustomerOnlySeed.ACCOUNT_HOLDER_ID,
            "accounts" to CustomerOnlySeed.CURRENT_ACCOUNT_ID,
            "account_identifiers" to CustomerOnlySeed.CURRENT_ACCOUNT_ID,
            "cards" to CustomerOnlySeed.CURRENT_ACCOUNT_ID,
            "ledger_entries" to CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        ).forEach { (table, accountScopedId) ->
            val column = if (table == "account_holders") "account_holder_id" else "account_id"
            val remaining = jdbc.queryForObject(
                "SELECT COUNT(*) FROM $table WHERE $column = ?",
                Long::class.java,
                accountScopedId,
            )!!
            assertThat(remaining).describedAs(table).isZero()
        }

        val systemUsers = jdbc.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Long::class.java,
            ClearingAccountsSeed.SYSTEM_USER_ID,
        )!!
        assertThat(systemUsers).isEqualTo(1L)

        verify(cognitoAdmin).adminDeleteUser(CustomerOnlySeed.EMAIL)
    }

    @Test
    fun `re-seed after clean reproduces the customer-only estate idempotently`() {
        seedCommand.run()
        cleanCommand.run()
        seedCommand.run()

        val accounts = jdbc.queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE user_id = ?",
            Long::class.java,
            CustomerOnlySeed.USER_ID,
        )!!
        assertThat(accounts).isEqualTo(1L)

        val activeDebitCards = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE account_id = ? AND variant = 'DEBIT' AND status = 'ACTIVE'",
            Long::class.java,
            CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(activeDebitCards).isEqualTo(1L)

        val ledgerEntries = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            CustomerOnlySeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(ledgerEntries).isGreaterThan(0L)
    }

    @Test
    fun `the audit hash chain verifies Ok after seeding the full estate`() {
        seedCommand.run()

        assertThat(auditChainVerifier.verify()).isInstanceOf(VerificationResult.Ok::class.java)
    }

    @Test
    fun `seeds an MDR worklist covering every outcome and ruling state with the ops reviewer ruling`() {
        seedCommand.run()

        val mdrDemoDecisionIds = MdrDemoSeed.DECISIONS.map { it.decisionId }
        val mdrDemoInClause = mdrDemoDecisionIds.joinToString(",") { "?" }

        val outcomes = jdbc.queryForList(
            "SELECT outcome, COUNT(*) AS n FROM mdr_entries " +
                "WHERE entry_kind = 'MODEL_DECISION' AND decision_id IN ($mdrDemoInClause) GROUP BY outcome",
            *mdrDemoDecisionIds.toTypedArray(),
        ).associate { it["outcome"] as String to (it["n"] as Number).toLong() }
        assertThat(outcomes).isEqualTo(mapOf("COMPLETED" to 5L, "TIMED_OUT" to 1L, "ERRORED" to 1L))

        val rulingsInForce = jdbc.queryForList(
            """
            SELECT d.decision_id,
                   (SELECT h.disposition FROM mdr_entries h
                     WHERE h.decision_id = d.decision_id AND h.entry_kind = 'HUMAN_DISPOSITION'
                     ORDER BY h.seq DESC LIMIT 1) AS ruling
              FROM mdr_entries d
             WHERE d.entry_kind = 'MODEL_DECISION' AND d.decision_id IN ($mdrDemoInClause)
             ORDER BY d.seq ASC
            """.trimIndent(),
            *mdrDemoDecisionIds.toTypedArray(),
        ).map { it["ruling"] as String? }
        assertThat(rulingsInForce).containsExactly("ACCEPT", "EDIT", "REJECT", "REJECT", null, null, null)

        val correctedRulingCount = jdbc.queryForObject(
            """
            SELECT COUNT(*) FROM mdr_entries
             WHERE entry_kind = 'HUMAN_DISPOSITION'
               AND decision_id = (SELECT decision_id FROM mdr_entries
                                   WHERE entry_kind = 'MODEL_DECISION' ORDER BY seq ASC LIMIT 1 OFFSET 3)
            """.trimIndent(),
            Long::class.java,
        )!!
        assertThat(correctedRulingCount).isEqualTo(2L)

        val ruleHumans = jdbc.queryForList(
            "SELECT DISTINCT human_id FROM mdr_entries WHERE entry_kind = 'HUMAN_DISPOSITION'",
            UUID::class.java,
        )
        assertThat(ruleHumans).containsExactly(OpsReviewerSeed.USER_ID)

        val advisoryFlags = jdbc.queryForList(
            "SELECT DISTINCT advisory FROM mdr_entries WHERE entry_kind = 'MODEL_DECISION'",
            Boolean::class.java,
        )
        assertThat(advisoryFlags).containsExactly(false)

        val correlationIds = jdbc.queryForList(
            "SELECT DISTINCT correlation_id FROM mdr_entries WHERE entry_kind = 'MODEL_DECISION'",
            String::class.java,
        )
        assertThat(correlationIds).contains(
            SarahMitchellSeed.PENDING_AUTH_1_ID.toString(),
            SarahMitchellSeed.CURRENT_ACCOUNT_ID.toString(),
            SarahMitchellSeed.SAVINGS_ACCOUNT_ID.toString(),
            SarahMitchellSeed.CREDIT_ACCOUNT_ID.toString(),
        )
    }

    @Test
    fun `re-seeding replays the fixed MDR entry ids and appends nothing new`() {
        seedCommand.run()
        val entriesAfterFirstSeed = jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java)!!
        val mdrAuditEventsAfterFirstSeed = jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action LIKE 'MDR_%'",
            Long::class.java,
        )!!

        cleanCommand.run()
        seedCommand.run()

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java))
            .isEqualTo(entriesAfterFirstSeed)
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action LIKE 'MDR_%'", Long::class.java))
            .isEqualTo(mdrAuditEventsAfterFirstSeed)
    }

    @Test
    fun `the mdr chain verifies Ok after seeding the demo worklist`() {
        seedCommand.run()

        assertThat(mdrChainVerifier.verify()).isInstanceOf(VerificationResult.Ok::class.java)
    }

    @Test
    fun `seeds five banking-day new customers created at run time with active debit cards and settled GBP deposits`() {
        seedCommand.run()

        val now = timeService.now()

        BankingDaySeed.NEW_CUSTOMERS.forEach { customer ->
            val holder = jdbc.queryForMap(
                "SELECT account_holder_id, account_holder_type, status, created_at FROM account_holders WHERE user_id = ?",
                customer.userId,
            )
            assertThat(holder["account_holder_id"]).describedAs(customer.email).isEqualTo(customer.accountHolderId)
            assertThat(holder["account_holder_type"]).isEqualTo("INDIVIDUAL")
            assertThat(holder["status"]).isEqualTo("ACTIVE")
            assertThat((holder["created_at"] as Timestamp).toInstant())
                .describedAs(customer.email)
                .isAfter(now.minus(1, ChronoUnit.DAYS))

            val card = jdbc.queryForMap(
                "SELECT variant, status, created_at FROM cards WHERE account_id = ?",
                customer.currentAccountId,
            )
            assertThat(card["variant"]).describedAs(customer.email).isEqualTo("DEBIT")
            assertThat(card["status"]).isEqualTo("ACTIVE")
            assertThat((card["created_at"] as Timestamp).toInstant()).isAfter(now.minus(1, ChronoUnit.DAYS))

            val deposit = jdbc.queryForMap(
                "SELECT direction, state, currency, amount_minor_units, beneficiary_account_id, created_at " +
                    "FROM payments WHERE payment_id = ?",
                customer.depositPaymentId,
            )
            assertThat(deposit["direction"]).describedAs(customer.email).isEqualTo("INBOUND")
            assertThat(deposit["state"]).isEqualTo("SETTLED")
            assertThat(deposit["currency"]).isEqualTo("GBP")
            assertThat(deposit["amount_minor_units"]).isEqualTo(customer.depositAmountMinor)
            assertThat(deposit["beneficiary_account_id"]).isEqualTo(customer.currentAccountId)
            assertThat((deposit["created_at"] as Timestamp).toInstant()).isAfter(now.minus(1, ChronoUnit.DAYS))
        }
    }

    @Test
    fun `the deposits figure reconciles with the sum of the seeded settled inbound deposits`() {
        seedCommand.run()

        val depositsTotal = jdbc.queryForObject(
            """
            SELECT COALESCE(SUM(amount_minor_units), 0)
              FROM payments
             WHERE direction = 'INBOUND'
               AND state = 'SETTLED'
               AND beneficiary_account_id IS NOT NULL
               AND currency = 'GBP'
            """.trimIndent(),
            Long::class.java,
        )!!
        assertThat(depositsTotal).isEqualTo(BankingDaySeed.DEPOSITS_TOTAL_MINOR)
        assertThat(BankingDaySeed.DEPOSITS_TOTAL_MINOR)
            .isEqualTo(BankingDaySeed.NEW_CUSTOMERS.sumOf { it.depositAmountMinor })
    }

    @Test
    fun `seeds referred payments declined end to end with PAYMENT correlated screening decisions ruled by the ops reviewer`() {
        seedCommand.run()

        BankingDaySeed.DECLINED_PAYMENTS.forEach { declined ->
            val payment = jdbc.queryForMap(
                "SELECT direction, state, currency, amount_minor_units, debtor_account_id, screening_decision_id " +
                    "FROM payments WHERE payment_id = ?",
                declined.paymentId,
            )
            assertThat(payment["direction"]).describedAs(declined.beneficiaryName).isEqualTo("OUTBOUND")
            assertThat(payment["state"]).isEqualTo("DECLINED")
            assertThat(payment["currency"]).isEqualTo("GBP")
            assertThat(payment["amount_minor_units"]).isEqualTo(declined.amountMinor)
            assertThat(payment["debtor_account_id"]).isEqualTo(SarahMitchellSeed.CURRENT_ACCOUNT_ID)
            assertThat(payment["screening_decision_id"]).isEqualTo(declined.decisionId)

            val decision = jdbc.queryForMap(
                """
                SELECT correlation_type, correlation_id, model_id, model_version, outcome, prompt_template_hash
                  FROM mdr_entries
                 WHERE decision_id = ? AND entry_kind = 'MODEL_DECISION'
                """.trimIndent(),
                declined.decisionId,
            )
            assertThat(decision["correlation_type"]).describedAs(declined.beneficiaryName).isEqualTo("PAYMENT")
            assertThat(decision["correlation_id"]).isEqualTo(declined.paymentId.toString())
            assertThat(decision["model_id"]).isEqualTo(BankingDaySeed.MODEL_ID)
            assertThat(decision["model_version"]).isEqualTo(BankingDaySeed.MODEL_VERSION)
            assertThat(decision["outcome"]).isEqualTo("COMPLETED")
            assertThat(decision["prompt_template_hash"]).describedAs("genuine prompt-template pin").isNotNull()

            val disposition = jdbc.queryForMap(
                """
                SELECT human_id, disposition
                  FROM mdr_entries
                 WHERE decision_id = ? AND entry_kind = 'HUMAN_DISPOSITION'
                """.trimIndent(),
                declined.decisionId,
            )
            assertThat(disposition["human_id"]).isEqualTo(OpsReviewerSeed.USER_ID)
            assertThat(disposition["disposition"]).isEqualTo("ACCEPT")
        }
    }

    @Test
    fun `the payments-stopped figure reconciles with the sum of the seeded declined payments`() {
        seedCommand.run()

        val paymentsStoppedTotal = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM payments WHERE state = 'DECLINED' AND currency = 'GBP'",
            Long::class.java,
        )!!
        assertThat(paymentsStoppedTotal).isEqualTo(BankingDaySeed.PAYMENTS_STOPPED_TOTAL_MINOR)
        assertThat(BankingDaySeed.PAYMENTS_STOPPED_TOTAL_MINOR)
            .isEqualTo(BankingDaySeed.DECLINED_PAYMENTS.sumOf { it.amountMinor })
    }

    @Test
    fun `seeded declined payments carry no ledger entries and no active hold`() {
        seedCommand.run()

        val placedHolds = jdbc.queryForObject(
            "SELECT COUNT(*) FROM holds WHERE account_id = ? AND status = 'PLACED'",
            Long::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        )!!
        assertThat(placedHolds).isEqualTo(2L)

        val declinedDebitAmounts = BankingDaySeed.DECLINED_PAYMENTS.map { -it.amountMinor }
        val declinedInClause = declinedDebitAmounts.joinToString(",") { "?" }
        val ledgerEntriesForDeclines = jdbc.queryForObject(
            "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ? AND amount_minor_units IN ($declinedInClause)",
            Long::class.java,
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
            *declinedDebitAmounts.toTypedArray(),
        )!!
        assertThat(ledgerEntriesForDeclines).isZero()
    }

    @Test
    fun `the banking-day screening decisions are additive - existing MdrDemoSeed entries are unchanged`() {
        seedCommand.run()

        val mdrDemoDecisionIds = MdrDemoSeed.DECISIONS.map { it.decisionId }
        val mdrDemoInClause = mdrDemoDecisionIds.joinToString(",") { "?" }

        val mdrDemoCorrelationTypes = jdbc.queryForList(
            "SELECT DISTINCT correlation_type FROM mdr_entries " +
                "WHERE entry_kind = 'MODEL_DECISION' AND decision_id IN ($mdrDemoInClause)",
            String::class.java,
            *mdrDemoDecisionIds.toTypedArray(),
        )
        assertThat(mdrDemoCorrelationTypes).containsExactlyInAnyOrder("CARD_AUTHORISATION", "ACCOUNT")

        val mdrDemoModelIds = jdbc.queryForList(
            "SELECT DISTINCT model_id FROM mdr_entries " +
                "WHERE entry_kind = 'MODEL_DECISION' AND decision_id IN ($mdrDemoInClause)",
            String::class.java,
            *mdrDemoDecisionIds.toTypedArray(),
        )
        assertThat(mdrDemoModelIds).containsExactly(MdrDemoSeed.MODEL_ID)

        val paymentDecisions = jdbc.queryForObject(
            "SELECT COUNT(*) FROM mdr_entries WHERE entry_kind = 'MODEL_DECISION' AND correlation_type = 'PAYMENT'",
            Long::class.java,
        )!!
        assertThat(paymentDecisions).isEqualTo(BankingDaySeed.DECLINED_PAYMENTS.size.toLong())
    }

    @Test
    fun `clean removes the banking-day cohort and every seeded payment surgically`() {
        seedCommand.run()
        cleanCommand.run()

        BankingDaySeed.NEW_CUSTOMERS.forEach { customer ->
            val users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Long::class.java,
                customer.email,
            )!!
            assertThat(users).describedAs(customer.email).isZero()

            val cards = jdbc.queryForObject(
                "SELECT COUNT(*) FROM cards WHERE account_id = ?",
                Long::class.java,
                customer.currentAccountId,
            )!!
            assertThat(cards).describedAs(customer.email).isZero()
        }

        val remainingPayments = jdbc.queryForObject("SELECT COUNT(*) FROM payments", Long::class.java)!!
        assertThat(remainingPayments).isZero()

        val mdrEntries = jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java)!!
        assertThat(mdrEntries).isGreaterThan(0L)
    }

    @Test
    fun `seeds a CLOSED_REPORTED aml case carrying the ruled SAR narrative and its alert`() {
        seedCommand.run()

        val reported = AmlDemoSeed.REPORTED_CASE
        val amlCase = jdbc.queryForMap(
            "SELECT account_id, status, alert_decision_ids, sar_decision_id, sar_narrative, closed_at " +
                "FROM aml_cases WHERE case_id = ?",
            reported.caseId,
        )
        assertThat(amlCase["account_id"]).isEqualTo(reported.customer.currentAccountId)
        assertThat(amlCase["status"]).isEqualTo("CLOSED_REPORTED")
        assertThat((amlCase["alert_decision_ids"] as java.sql.Array).array as Array<*>)
            .containsExactlyElementsOf(reported.alertDecisionIds)
        assertThat(amlCase["sar_decision_id"]).isEqualTo(reported.sar!!.decisionId)
        assertThat(amlCase["sar_narrative"] as String)
            .contains("account ${reported.customer.currentAccountId}")
            .contains("case ${reported.caseId}")
        assertThat(amlCase["closed_at"]).isNotNull()
    }

    @Test
    fun `seeds an OPEN aml case whose two alerts are recorded but not yet ruled`() {
        seedCommand.run()

        val open = AmlDemoSeed.OPEN_CASE
        val amlCase = jdbc.queryForMap(
            "SELECT account_id, status, alert_decision_ids, sar_decision_id, sar_narrative, closed_at " +
                "FROM aml_cases WHERE case_id = ?",
            open.caseId,
        )
        assertThat(amlCase["account_id"]).isEqualTo(open.customer.currentAccountId)
        assertThat(amlCase["status"]).isEqualTo("OPEN")
        assertThat((amlCase["alert_decision_ids"] as java.sql.Array).array as Array<*>)
            .containsExactlyElementsOf(open.alertDecisionIds)
        assertThat(amlCase["sar_decision_id"]).isNull()
        assertThat(amlCase["sar_narrative"]).isNull()
        assertThat(amlCase["closed_at"]).isNull()

        open.alerts.forEach { alert ->
            val decisionId = alert.decisionId(open.customer.currentAccountId)
            val decision = jdbc.queryForMap(
                "SELECT correlation_type, correlation_id, model_id, advisory, outcome, verdict " +
                    "FROM mdr_entries WHERE decision_id = ? AND entry_kind = 'MODEL_DECISION'",
                decisionId,
            )
            assertThat(decision["correlation_type"]).describedAs(alert.typology).isEqualTo("ACCOUNT")
            assertThat(decision["correlation_id"]).isEqualTo(open.customer.currentAccountId.toString())
            assertThat(decision["model_id"]).isEqualTo(AmlDemoSeed.ALERT_MODEL_ID)
            assertThat(decision["advisory"]).isEqualTo(false)
            assertThat(decision["outcome"]).isEqualTo("COMPLETED")
            assertThat(decision["verdict"].toString()).contains("\"typology\": \"${alert.typology}\"")

            val rulings = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mdr_entries WHERE decision_id = ? AND entry_kind = 'HUMAN_DISPOSITION'",
                Long::class.java,
                decisionId,
            )!!
            assertThat(rulings).describedAs(alert.typology).isZero()
        }
    }

    @Test
    fun `the reported case's arc is on the mdr chain - alert accepted then SAR drafted from it and accepted`() {
        seedCommand.run()

        val reported = AmlDemoSeed.REPORTED_CASE
        val accountId = reported.customer.currentAccountId
        val alert = reported.alerts.single()
        val alertDecisionId = alert.decisionId(accountId)

        val alertRuling = jdbc.queryForMap(
            "SELECT entry_id, human_id, disposition FROM mdr_entries " +
                "WHERE decision_id = ? AND entry_kind = 'HUMAN_DISPOSITION'",
            alertDecisionId,
        )
        assertThat(alertRuling["entry_id"]).isEqualTo(alert.rulingEntryId)
        assertThat(alertRuling["human_id"]).isEqualTo(OpsReviewerSeed.USER_ID)
        assertThat(alertRuling["disposition"]).isEqualTo("ACCEPT")

        val sar = reported.sar!!
        val sarDecision = jdbc.queryForMap(
            "SELECT entry_id, correlation_type, correlation_id, model_id, prompt_template_hash, " +
                "outcome, raw_output, retrieved_context FROM mdr_entries " +
                "WHERE decision_id = ? AND entry_kind = 'MODEL_DECISION'",
            sar.decisionId,
        )
        assertThat(sarDecision["entry_id"]).isEqualTo(sar.entryId)
        assertThat(sarDecision["correlation_type"]).isEqualTo("CASE")
        assertThat(sarDecision["correlation_id"]).isEqualTo(reported.caseId.toString())
        assertThat(sarDecision["model_id"]).isEqualTo(AmlDemoSeed.SAR_MODEL_ID)
        assertThat(hex(sarDecision["prompt_template_hash"] as ByteArray))
            .describedAs("the genuine sha256 of aml-service's sar-narrative-v1 template")
            .isEqualTo(AmlDemoSeed.SAR_PROMPT_TEMPLATE_HASH)
        assertThat(sarDecision["outcome"]).isEqualTo("COMPLETED")

        // The Gate's contract: an ACCEPT in force serves raw output whose "narrative" is what the
        // case filed, so the record and the closed row say the same thing.
        val filedNarrative = jdbc.queryForObject(
            "SELECT sar_narrative FROM aml_cases WHERE case_id = ?",
            String::class.java,
            reported.caseId,
        )!!
        assertThat(sarDecision["raw_output"].toString()).contains(filedNarrative.substringBefore("\n\n"))

        // Grounding: the draft cites the alert and every payment the alert fired over, nothing else.
        val citedIds = jdbc.queryForList(
            "SELECT jsonb_array_elements(retrieved_context) ->> 'id' AS id FROM mdr_entries WHERE entry_id = ?",
            String::class.java,
            sar.entryId,
        )
        assertThat(citedIds).containsExactlyInAnyOrderElementsOf(
            listOf(alertDecisionId.toString()) + alert.evidence.map { it.paymentId.toString() },
        )

        val sarRuling = jdbc.queryForMap(
            "SELECT entry_id, human_id, disposition FROM mdr_entries " +
                "WHERE decision_id = ? AND entry_kind = 'HUMAN_DISPOSITION'",
            sar.decisionId,
        )
        assertThat(sarRuling["entry_id"]).isEqualTo(sar.rulingEntryId)
        assertThat(sarRuling["human_id"]).isEqualTo(OpsReviewerSeed.USER_ID)
        assertThat(sarRuling["disposition"]).isEqualTo("ACCEPT")
    }

    @Test
    fun `every alert cites its evidence payments by the content hash of the rows actually seeded`() {
        seedCommand.run()

        AmlDemoSeed.CASES.forEach { monitoredCase ->
            monitoredCase.alerts.forEach { alert ->
                val citedHashes = jdbc.queryForList(
                    """
                    SELECT ref ->> 'id' AS id, ref ->> 'contentHash' AS content_hash
                      FROM mdr_entries, jsonb_array_elements(retrieved_context) AS ref
                     WHERE entry_id = ? AND ref ->> 'type' = 'PAYMENT'
                    """.trimIndent(),
                    alert.entryId(monitoredCase.customer.currentAccountId),
                ).associate { it["id"] as String to it["content_hash"] as String }

                assertThat(citedHashes).describedAs(alert.typology)
                    .containsOnlyKeys(*alert.evidence.map { it.paymentId.toString() }.toTypedArray())

                alert.evidence.forEach { evidence ->
                    val payment = jdbc.queryForMap(
                        "SELECT rail, direction, state, amount_minor_units, currency, created_at, " +
                            "beneficiary_name, beneficiary_sort_code, beneficiary_account_no, " +
                            "beneficiary_iban, beneficiary_bic, debtor_account_id " +
                            "FROM payments WHERE payment_id = ?",
                        evidence.paymentId,
                    )
                    assertThat(payment["state"]).describedAs(evidence.description).isEqualTo("SETTLED")
                    assertThat(payment["direction"]).isEqualTo("OUTBOUND")
                    assertThat(payment["rail"]).isEqualTo(evidence.rail)
                    assertThat(payment["amount_minor_units"]).isEqualTo(evidence.amountMinorUnits)
                    assertThat(payment["beneficiary_iban"]).isEqualTo(evidence.beneficiaryIban)
                    assertThat(payment["beneficiary_bic"]).isEqualTo(evidence.beneficiaryBic)
                    assertThat(payment["debtor_account_id"]).isEqualTo(monitoredCase.customer.currentAccountId)

                    val settledAt = (payment["created_at"] as Timestamp).toInstant()
                    assertThat(citedHashes[evidence.paymentId.toString()])
                        .describedAs("content hash pins the payment row as monitoring saw it")
                        .isEqualTo(evidence.contentHashAt(settledAt))
                }
            }
        }
    }

    @Test
    fun `each evidence payment posts to the ledger so the monitored customer's own feed shows it`() {
        seedCommand.run()

        AmlDemoSeed.CASES.forEach { monitoredCase ->
            monitoredCase.evidence.forEach { evidence ->
                val debits = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ledger_entries WHERE account_id = ? AND amount_minor_units = ? " +
                        "AND description = ?",
                    Long::class.java,
                    monitoredCase.customer.currentAccountId,
                    -evidence.amountMinorUnits,
                    evidence.description,
                )!!
                assertThat(debits).describedAs(evidence.description).isGreaterThan(0L)
            }
        }
    }

    @Test
    fun `the live-fire persona is left with headroom to fire five sub-threshold payments on stage`() {
        seedCommand.run()

        val livefire = AmlDemoSeed.REPORTED_CASE.customer
        val ledgerBalance = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM ledger_entries WHERE account_id = ?",
            Long::class.java,
            livefire.currentAccountId,
        )!!
        val heldMinorUnits = jdbc.queryForObject(
            "SELECT COALESCE(SUM(amount_minor_units), 0) FROM holds WHERE account_id = ? AND status = 'PLACED'",
            Long::class.java,
            livefire.currentAccountId,
        )!!

        assertThat(ledgerBalance - heldMinorUnits)
            .describedAs("five payments of just under the £10,000 threshold, jointly at it")
            .isGreaterThanOrEqualTo(AmlDemoSeed.STRUCTURING_THRESHOLD_MINOR_UNITS)
    }

    @Test
    fun `the case transitions are on the audit chain - one opening per case and the reported close`() {
        seedCommand.run()

        val amlEvents = jdbc.queryForList(
            "SELECT action, resource_id, actor_type, actor_id, before_state, after_state FROM audit_events " +
                "WHERE action LIKE 'AML\\_%' ORDER BY seq ASC",
        )
        assertThat(amlEvents.map { "${it["action"]} ${it["resource_id"]}" })
            .describedAs("each case's transitions land in the order they happened")
            .containsExactly(
                "AML_CASE_OPENED ${AmlDemoSeed.REPORTED_CASE.caseId}",
                "AML_CASE_CLOSED_REPORTED ${AmlDemoSeed.REPORTED_CASE.caseId}",
                "AML_CASE_OPENED ${AmlDemoSeed.OPEN_CASE.caseId}",
            )

        val opened = amlEvents.first { it["resource_id"] == AmlDemoSeed.REPORTED_CASE.caseId.toString() }
        assertThat(opened["actor_type"]).isEqualTo("SYSTEM")
        assertThat(opened["actor_id"]).isNull()
        assertThat(opened["after_state"].toString())
            .contains(AmlDemoSeed.REPORTED_CASE.customer.currentAccountId.toString())
            .contains("OPEN")

        val closed = amlEvents.single { it["action"] == "AML_CASE_CLOSED_REPORTED" }
        assertThat(closed["resource_id"]).isEqualTo(AmlDemoSeed.REPORTED_CASE.caseId.toString())
        assertThat(closed["actor_type"]).isEqualTo("ADMIN")
        assertThat(closed["actor_id"]).isEqualTo(OpsReviewerSeed.USER_ID)
        assertThat(closed["after_state"].toString())
            .contains(AmlDemoSeed.REPORTED_CASE.sar!!.decisionId.toString())
            .contains(AmlDemoSeed.REPORTED_CASE.sar.rulingEntryId.toString())
            .contains("ACCEPT")
    }

    @Test
    fun `re-seeding after clean rebuilds both aml cases without duplicating a case or an mdr entry`() {
        seedCommand.run()
        val mdrEntriesAfterFirstSeed = jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java)!!

        cleanCommand.run()

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM aml_cases", Long::class.java)).isZero()

        seedCommand.run()

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java))
            .describedAs("fixed entry ids replay; the append-only chain gains nothing")
            .isEqualTo(mdrEntriesAfterFirstSeed)

        AmlDemoSeed.CASES.forEach { monitoredCase ->
            val cases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM aml_cases WHERE account_id = ?",
                Long::class.java,
                monitoredCase.customer.currentAccountId,
            )!!
            assertThat(cases).describedAs(monitoredCase.customer.email).isEqualTo(1L)
        }
    }

    @Test
    fun `seeds each monitored customer into the customer Cognito group only - investigation grants no staff surface`() {
        seedCommand.run()

        AmlDemoSeed.MONITORED_CUSTOMERS.forEach { customer ->
            verify(cognitoAdmin).adminCreateUser(customer.email, customer.fullName)
            verify(cognitoAdmin).adminSetUserPassword(customer.email, customer.demoPassword, permanent = true)

            val groups = mockingDetails(cognitoAdmin).invocations
                .filter { it.method.name == "adminAddUserToGroup" && it.getArgument<String>(0) == customer.email }
                .map { it.getArgument<String>(1) }
            assertThat(groups).describedAs(customer.email).containsExactly("customer")
        }
    }

    @Test
    fun `clean removes the monitored customers their cases and their Cognito identities`() {
        seedCommand.run()
        cleanCommand.run()

        AmlDemoSeed.CASES.forEach { monitoredCase ->
            val customer = monitoredCase.customer
            val users = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = ?",
                Long::class.java,
                customer.email,
            )!!
            assertThat(users).describedAs(customer.email).isZero()

            val cases = jdbc.queryForObject(
                "SELECT COUNT(*) FROM aml_cases WHERE case_id = ?",
                Long::class.java,
                monitoredCase.caseId,
            )!!
            assertThat(cases).describedAs(customer.email).isZero()

            verify(cognitoAdmin).adminDeleteUser(customer.email)
        }

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mdr_entries", Long::class.java))
            .describedAs("the alerts and the SAR stay on the append-only chain")
            .isGreaterThan(0L)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private fun seedSchemeSettlement() {
        userRepository.save(
            UserEntity(
                id = ClearingAccountsSeed.SYSTEM_USER_ID,
                email = ClearingAccountsSeed.SYSTEM_EMAIL,
                firstName = "System",
                lastName = "Clearing",
                dateOfBirth = LocalDate.of(1970, 1, 1),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = timeService.now().minus(60, ChronoUnit.DAYS),
                createdBy = "seed-command-it",
            )
        )
        accountHolderRepository.save(
            AccountHolderEntity(
                accountHolderId = ClearingAccountsSeed.SYSTEM_ACCOUNT_HOLDER_ID,
                userId = ClearingAccountsSeed.SYSTEM_USER_ID,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = timeService.now().minus(60, ChronoUnit.DAYS),
                status = OnboardingStatus.ACTIVE,
            )
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
