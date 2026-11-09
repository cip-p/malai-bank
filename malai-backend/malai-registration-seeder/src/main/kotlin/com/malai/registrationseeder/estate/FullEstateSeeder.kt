package com.malai.registrationseeder.estate

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.identifier.AccountIdentifierFactory
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.registrationseeder.card.CardSeedHelper
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.registrationseeder.data.CreditEstateSeed
import com.malai.registrationseeder.data.UkMerchantsSeed
import com.malai.registrationseeder.ledger.LedgerSeedHelper
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * Brings a self-registered user's estate up to parity with the canonical Sarah
 * Mitchell demo: a credit-card account with settled purchase history and a
 * part-payment, a savings account with an opening balance, and live pending
 * card authorisations on the CURRENT account.
 *
 * Rows are written directly rather than through `AccountsService.applyForCreditCard`
 * / `createAccount`, which is the counter-intuitive choice and worth stating.
 * Those product paths look like the obvious reuse, but they call `ledgerClient`,
 * `cardClient` and the S2S token client over HTTP. That would put internal HTTP
 * requests inside the registration hook's database transaction, make seeding
 * depend on the app's own HTTP surface being reachable mid-registration, and it
 * simply does not resolve in this module's test slice (`UnknownHostException:
 * test-only`). Every other seeder here — [LedgerSeedHelper], [CardSeedHelper],
 * InstrumentSeedHelper, and malai-seeder's own SeedCommand — writes directly for
 * the same reason.
 *
 * What is *not* reimplemented is identifier generation: [AccountIdentifierFactory]
 * is injected, so sort code, IBAN/BIC and account number come from the same
 * Postgres sequence and bank-identifier properties as every other account in the
 * system rather than from seeder-local invention.
 *
 * Every step is individually idempotent — each is skipped when its account or
 * rows already exist — so a re-seed adds nothing rather than duplicating an
 * estate.
 */
@Component
class FullEstateSeeder(
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountIdentifierFactory: AccountIdentifierFactory,
    private val ledgerSeedHelper: LedgerSeedHelper,
    private val cardSeedHelper: CardSeedHelper,
    private val auditWriter: AuditWriter,
    private val jdbcTemplate: JdbcTemplate,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class EstateResult(
        val creditAccountId: UUID?,
        val creditPostings: Int,
        val savingsAccountId: UUID?,
        val pendingAuthorisations: Int,
    )

    fun seedFor(
        userId: UUID,
        accountHolderId: UUID,
        currentAccountId: UUID,
        cardholderName: String,
        random: Random,
    ): EstateResult {
        val now = timeService.now()

        val credit = seedCreditEstate(userId, accountHolderId, cardholderName, random, now)
        val savingsAccountId = seedSavingsAccount(userId, accountHolderId, now)
        val authorisations = seedPendingAuthorisations(currentAccountId, accountHolderId, now)

        return EstateResult(
            creditAccountId = credit?.first,
            creditPostings = credit?.second ?: 0,
            savingsAccountId = savingsAccountId,
            pendingAuthorisations = authorisations,
        )
    }

    /**
     * Purchases post negative on the credit side — a purchase increases what is
     * owed — against SCHEME_SETTLEMENT, matching how a settled card payment
     * lands at runtime. The repayment posts positive. Their net is what the
     * statement's minimum-payment calculation reads.
     *
     * The ledger account is FLOORED at the negative credit limit, exactly as
     * `applyForCreditCard` registers it, so settled purchases can push the
     * balance negative down to the limit and no further.
     */
    private fun seedCreditEstate(
        userId: UUID,
        accountHolderId: UUID,
        cardholderName: String,
        random: Random,
        now: Instant,
    ): Pair<UUID, Int>? {
        if (accountRepository.existsByAccountHolderIdAndAccountType(accountHolderId, AccountType.CREDIT_CARD)) {
            return null
        }

        val createdAt = now.minus(90, ChronoUnit.DAYS)
        val creditAccountId = uuidGenerator.generate()

        accountRepository.save(
            AccountEntity(
                accountId = creditAccountId,
                accountType = AccountType.CREDIT_CARD,
                defaultCategory = uuidGenerator.generate(),
                currency = Currency.GBP,
                createdAt = createdAt,
                name = CreditEstateSeed.CREDIT_ACCOUNT_NAME,
                userId = userId,
                accountHolderId = accountHolderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
        jdbcTemplate.update(
            "INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy, floor_minor_units) " +
                "VALUES (?, 'GBP', 'CUSTOMER', 'FLOORED', ?) ON CONFLICT (account_id) DO NOTHING",
            creditAccountId,
            CreditEstateSeed.CREDIT_FLOOR_MINOR,
        )
        accountIdentifierRepository.saveAll(accountIdentifierFactory.identifiersFor(creditAccountId))
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_CREATED",
                resourceType = "Account",
                resourceId = creditAccountId.toString(),
                afterState = mapOf(
                    "type" to AccountType.CREDIT_CARD.name,
                    "name" to CreditEstateSeed.CREDIT_ACCOUNT_NAME,
                ),
            )
        )

        cardSeedHelper.issueCard(
            accountId = creditAccountId,
            accountHolderId = accountHolderId,
            cardholderName = cardholderName,
            variant = "CREDIT",
            issuedAt = createdAt,
            random = random,
        )

        CreditEstateSeed.PURCHASES.forEach { purchase ->
            ledgerSeedHelper.postBalancedPair(
                postedAt = now.minus(purchase.daysAgo, ChronoUnit.DAYS),
                currentSideAccountId = creditAccountId,
                counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
                counterpartyName = purchase.merchantName,
                amountOnCurrentSide = -purchase.amountMinor,
                description = purchase.merchantName,
                type = "CARD_PAYMENT",
                initiatedById = accountHolderId,
            )
        }

        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(15, ChronoUnit.DAYS),
            currentSideAccountId = creditAccountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = "Payment received",
            amountOnCurrentSide = CreditEstateSeed.CREDIT_REPAYMENT_MINOR,
            description = UkMerchantsSeed.CC_PAYMENT,
            type = "INCOMING_TRANSFER",
            initiatedById = accountHolderId,
        )

        return creditAccountId to (CreditEstateSeed.PURCHASES.size + 1)
    }

    /**
     * Savings was previously a seeder-only extra that self-registered users did
     * not get. It is included here so a registrant's account list matches the
     * demo customer's.
     *
     * The opening balance posts against SCHEME_SETTLEMENT rather than moving
     * money out of the CURRENT account, so seeding savings cannot disturb the
     * closing balance the dashboard has already been seeded to land on.
     */
    private fun seedSavingsAccount(userId: UUID, accountHolderId: UUID, now: Instant): UUID? {
        if (accountRepository.existsByAccountHolderIdAndAccountType(accountHolderId, AccountType.SAVINGS)) {
            return null
        }

        val createdAt = now.minus(31, ChronoUnit.DAYS)
        val savingsAccountId = uuidGenerator.generate()

        accountRepository.save(
            AccountEntity(
                accountId = savingsAccountId,
                accountType = AccountType.SAVINGS,
                defaultCategory = uuidGenerator.generate(),
                currency = Currency.GBP,
                createdAt = createdAt,
                name = CreditEstateSeed.SAVINGS_ACCOUNT_NAME,
                userId = userId,
                accountHolderId = accountHolderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
        jdbcTemplate.update(
            "INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy) " +
                "VALUES (?, 'GBP', 'CUSTOMER', 'NO_OVERDRAFT') ON CONFLICT (account_id) DO NOTHING",
            savingsAccountId,
        )
        accountIdentifierRepository.saveAll(accountIdentifierFactory.identifiersFor(savingsAccountId))
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_CREATED",
                resourceType = "Account",
                resourceId = savingsAccountId.toString(),
                afterState = mapOf(
                    "type" to AccountType.SAVINGS.name,
                    "name" to CreditEstateSeed.SAVINGS_ACCOUNT_NAME,
                ),
            )
        )

        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(30, ChronoUnit.DAYS),
            currentSideAccountId = savingsAccountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = ClearingAccountsSeed.ACME.name,
            amountOnCurrentSide = CreditEstateSeed.SAVINGS_OPENING_MINOR,
            description = "Opening balance",
            type = "INCOMING_TRANSFER",
            initiatedById = accountHolderId,
        )

        return savingsAccountId
    }

    /**
     * Live authorisations against the real debit card, each paired with a
     * PLACED ledger hold so the CURRENT account's available balance genuinely
     * drops (available = ledger − Σ PLACED holds). Not a display fake.
     *
     * `hold_id == authorisation_id`, matching what the runtime authorise path
     * does, so a later settlement or expiry reconciles against the same id.
     */
    private fun seedPendingAuthorisations(currentAccountId: UUID, accountHolderId: UUID, now: Instant): Int {
        val debitCardId = jdbcTemplate.query(
            "SELECT card_id FROM cards WHERE account_id = ? AND variant = 'DEBIT'",
            { rs, _ -> rs.getObject("card_id", UUID::class.java) },
            currentAccountId,
        ).firstOrNull()

        if (debitCardId == null) {
            log.warn("estate: no debit card on CURRENT account {}; skipping authorisations", currentAccountId)
            return 0
        }

        val alreadyPending = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM card_authorisations WHERE account_id = ? AND status = 'AUTHORISED'",
            Int::class.java,
            currentAccountId,
        ) ?: 0
        if (alreadyPending > 0) {
            return 0
        }

        CreditEstateSeed.PENDING_AUTHORISATIONS.forEach { auth ->
            val authorisationId = uuidGenerator.generate()
            val placedAt = now.minus(auth.minutesAgo, ChronoUnit.MINUTES)

            ledgerSeedHelper.placeHold(
                holdId = authorisationId,
                accountId = currentAccountId,
                amountMinorUnits = auth.amountMinor,
                placedAt = placedAt,
            )
            cardSeedHelper.placeAuthorisation(
                authorisationId = authorisationId,
                cardId = debitCardId,
                accountId = currentAccountId,
                accountHolderId = accountHolderId,
                schemeReference = uuidGenerator.generate(),
                merchantName = auth.merchantName,
                mcc = auth.mcc,
                amountMinorUnits = auth.amountMinor,
                currency = Currency.GBP.name,
                holdId = authorisationId,
                createdAt = placedAt,
            )
        }

        return CreditEstateSeed.PENDING_AUTHORISATIONS.size
    }
}
