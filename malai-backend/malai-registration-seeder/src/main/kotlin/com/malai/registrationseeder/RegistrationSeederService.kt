package com.malai.registrationseeder

import com.malai.accounts.entity.AccountType
import com.malai.accounts.repository.AccountRepository
import com.malai.auth.repository.UserRepository
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Frequency
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.schedule.OccurrenceSchedule
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.registrationseeder.data.ForecastSeed
import com.malai.registrationseeder.card.CardSeedHelper
import com.malai.registrationseeder.data.RegistrationInstrumentsSeed
import com.malai.registrationseeder.data.UkMerchantsSeed
import com.malai.registrationseeder.estate.FullEstateSeeder
import com.malai.registrationseeder.instrument.InstrumentSeedHelper
import com.malai.registrationseeder.ledger.LedgerSeedHelper
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.random.Random

/**
 * Populates a freshly-registered user's CURRENT account with a "Sarah Mitchell
 * flavour" dataset — one salary credit at day -30 + 19..29 daily spending
 * postings spread across the last 29 days — so the webapp dashboard lands on
 * realistic data immediately after registration (D004, SITE-07).
 *
 * The single `@Transactional` boundary matches the wider seeder pattern: every
 * balanced pair the helper writes is validated by the V5 deferred sum-to-zero
 * trigger at COMMIT, and the audit chain ends in a consistent state. Callers
 * (HTTP endpoint in S02, RegistrationOrchestrator in S03, CLI in T03) don't
 * need to manage their own transaction — they just call [seedFor].
 *
 * Idempotency is keyed on "the target CURRENT account has ≥1 ledger entry":
 * the second call on an already-seeded account returns [SeedResult.Noop]
 * without writing a row.
 *
 * Two entry points share one body. [seedFor] (email only) is the registration
 * path: a fresh RNG per call, so every self-registered user gets a distinct
 * dataset. [seedFor] (email + seed) is the canonical-demo path the malai-seeder
 * CLI drives for Sarah Mitchell — the same seed reproduces identical amounts,
 * counts, opening balance and instrument/transaction UUIDs on every reset, so
 * the buyer-side webapp can pin screenshots / Storybook against a byte-stable
 * dataset. (Absolute posted-at timestamps still track `timeService.now()`, as
 * the seeder has never stubbed time.)
 */
@Component
class RegistrationSeederService(
    private val userRepository: UserRepository,
    private val accountRepository: AccountRepository,
    private val ledgerSeedHelper: LedgerSeedHelper,
    private val instrumentSeedHelper: InstrumentSeedHelper,
    private val cardSeedHelper: CardSeedHelper,
    private val fullEstateSeeder: FullEstateSeeder,
    private val timeService: TimeService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun seedFor(email: String): SeedResult =
        doSeed(email, Random(System.nanoTime()), null, warningForecast = true, fullEstate = true)

    // `fullEstate = false` on the deterministic path on purpose. This overload is
    // the canonical Sarah Mitchell route driven by the malai-seeder CLI, and
    // SeedCommand already builds her credit and savings estate from fixed UUIDs so
    // her dataset stays byte-stable for pinned screenshots. Letting FullEstateSeeder
    // also open accounts for her would either collide with those or generate fresh
    // ids and break that stability.
    @Transactional
    fun seedFor(email: String, seed: Long): SeedResult =
        doSeed(email, Random(seed), deterministicUuidGenerator(seed), warningForecast = false, fullEstate = false)

    private fun doSeed(
        email: String,
        random: Random,
        uuids: UuidGenerator?,
        warningForecast: Boolean,
        fullEstate: Boolean,
    ): SeedResult {
        val startedAt = System.nanoTime()

        val user = userRepository.findByEmail(email).orElse(null)
        if (user == null) {
            return logAndReturn(SeedResult.UserNotFound(email), email, startedAt)
        }

        val currentAccount = accountRepository.findByUserId(user.id)
            .firstOrNull { it.accountType == AccountType.PRIMARY && it.status == OnboardingStatus.ACTIVE }
        if (currentAccount == null) {
            return logAndReturn(SeedResult.NoActiveCurrentAccount(user.id), email, startedAt)
        }

        val existingCount = ledgerSeedHelper.countEntriesForAccount(currentAccount.accountId)
        if (existingCount > 0) {
            return logAndReturn(
                SeedResult.Noop(user.id, currentAccount.accountId, existingCount.toInt()),
                email,
                startedAt,
            )
        }

        val transactionCount = random.nextInt(TX_COUNT_MIN, TX_COUNT_MAX + 1)
        val openingBalanceMinor = random.nextLong(OPENING_MIN_MINOR, OPENING_MAX_MINOR + 1)
        val initiatedById = currentAccount.accountHolderId
        val now = timeService.now()
        val spendingCount = transactionCount - 1

        val generalSlots = (0 until GENERAL_SPENDING_COUNT)
            .associate { k -> (k * spendingCount / GENERAL_SPENDING_COUNT) to GENERAL_SPENDINGS[k] }
        val spendings = (0 until spendingCount).map { i ->
            val daysAgo = (i.toLong() * 29 / spendingCount) + 1
            val amount = -random.nextLong(SPENDING_MIN_MINOR, SPENDING_MAX_MINOR + 1)
            when (val general = generalSlots[i]) {
                null -> SpendingPosting(
                    daysAgo,
                    ClearingAccountsSeed.ALL[(i + 1) % ClearingAccountsSeed.ALL.size].name,
                    SPENDING_NARRATIVES[i % SPENDING_NARRATIVES.size],
                    "TRANSFER",
                    amount,
                )
                else -> SpendingPosting(daysAgo, general.merchantName, general.description, general.type, amount)
            }
        }

        // Salary credit on day -30 absorbs the residual so the closing balance ==
        // openingBalanceMinor by construction.
        val salaryAmount = openingBalanceMinor - spendings.sumOf { it.amount }

        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(30, ChronoUnit.DAYS),
            currentSideAccountId = currentAccount.accountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = ClearingAccountsSeed.ACME.name,
            amountOnCurrentSide = salaryAmount,
            description = UkMerchantsSeed.ACME_SALARY,
            type = "INCOMING_TRANSFER",
            initiatedById = initiatedById,
            idGenerator = uuids,
        )
        spendings.forEach { p ->
            ledgerSeedHelper.postBalancedPair(
                postedAt = now.minus(p.daysAgo, ChronoUnit.DAYS),
                currentSideAccountId = currentAccount.accountId,
                counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
                counterpartyName = p.counterpartyName,
                amountOnCurrentSide = p.amount,
                description = p.description,
                type = p.type,
                initiatedById = initiatedById,
                idGenerator = uuids,
            )
        }

        val completedForecastPostings = if (warningForecast) {
            seedWarningForecast(
                user.id, currentAccount.accountHolderId, currentAccount.accountId,
                openingBalanceMinor, initiatedById, now, uuids,
            )
        } else {
            seedInstruments(user.id, currentAccount.accountHolderId, currentAccount.accountId, now, uuids)
            0
        }

        // account-service auto-issues the CURRENT account's ACTIVE debit card when it onboards the
        // account (the runtime register/verify path), so only issue here when nobody has — the CLI
        // and IT fixtures create the account raw and arrive card-less. A second ACTIVE card would
        // trip cards_one_active_per_account.
        if (!cardSeedHelper.hasActiveCard(currentAccount.accountId)) {
            cardSeedHelper.issueCard(
                accountId = currentAccount.accountId,
                accountHolderId = currentAccount.accountHolderId,
                cardholderName = listOfNotNull(user.firstName, user.lastName).joinToString(" "),
                variant = "DEBIT",
                issuedAt = now.minus(31, ChronoUnit.DAYS),
                random = random,
                idGenerator = uuids,
            )
        }

        // After the debit card, because the pending authorisations attach to it.
        val estate = if (fullEstate) {
            fullEstateSeeder.seedFor(
                userId = user.id,
                accountHolderId = currentAccount.accountHolderId,
                currentAccountId = currentAccount.accountId,
                cardholderName = listOfNotNull(user.firstName, user.lastName).joinToString(" "),
                random = random,
            )
        } else {
            null
        }

        return logAndReturn(
            SeedResult.Seeded(
                user.id, currentAccount.accountId, transactionCount, openingBalanceMinor,
                completedForecastPostings,
                creditAccountId = estate?.creditAccountId,
                creditPostings = estate?.creditPostings ?: 0,
                savingsAccountId = estate?.savingsAccountId,
                pendingAuthorisations = estate?.pendingAuthorisations ?: 0,
            ),
            email,
            startedAt,
        )
    }

    // A few saved payees, ACTIVE standing orders linked to them, and ACTIVE
    // fixed-amount mandates, anchored a day or three ahead so the executors fire
    // while the fresh user is still exploring. Payees are inserted first so the
    // standing-order foreign key resolves within this same @Transactional commit.
    private fun seedInstruments(userId: UUID, accountHolderId: UUID, accountId: UUID, now: Instant, uuids: UuidGenerator?) {
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)

        val payeeIds = RegistrationInstrumentsSeed.PAYEES.map { payee ->
            instrumentSeedHelper.insertPayee(
                accountHolderId = accountHolderId,
                name = payee.name,
                sortCode = payee.clearing.sortCode,
                accountNumber = payee.clearing.accountNumber,
                nickname = payee.nickname,
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
        }

        RegistrationInstrumentsSeed.STANDING_ORDERS.forEach { order ->
            val payee = RegistrationInstrumentsSeed.PAYEES[order.payeeTemplateIndex]
            val startDate = today.plusDays(order.startOffsetDays)
            instrumentSeedHelper.insertStandingOrder(
                accountId = accountId,
                payeeId = payeeIds[order.payeeTemplateIndex],
                payeeName = payee.name,
                payeeSortCode = payee.clearing.sortCode,
                payeeAccountNumber = payee.clearing.accountNumber,
                reference = order.reference,
                amountMinorUnits = order.amountMinor,
                frequency = order.frequency,
                startDate = startDate,
                nextPaymentAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
        }

        RegistrationInstrumentsSeed.DIRECT_DEBITS.forEach { mandate ->
            val startDate = today.plusDays(mandate.startOffsetDays)
            instrumentSeedHelper.insertDirectDebit(
                accountId = accountId,
                creditorName = mandate.creditorName,
                creditorSortCode = mandate.creditor.sortCode,
                creditorAccountNumber = mandate.creditor.accountNumber,
                reference = mandate.reference,
                amountMinorUnits = mandate.amountMinor,
                frequency = mandate.frequency,
                startDate = startDate,
                nextCollectionAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
        }
    }

    // The warning-tripping forecast every self-registered user receives (balance-
    // forecast issue 03), kept off the canonical Sarah Mitchell path so her pinned
    // dataset does not gain clock-relative completed postings. Two recurring
    // payments already completed earlier this month — each with a matching backdated
    // ledger posting so the available balance reflects it and it shows in the
    // transactions feed — and two still expected before month-end, with the expected
    // rent sized off the post-completion available balance so the projected month-end
    // balance lands ForecastSeed.OVERSHOOT_MINOR in the red. Completed instruments are
    // anchored in the past but scheduled forward by [occurrenceAfterToday], so none of
    // them is born past-due. Payees are inserted first so the standing-order foreign
    // key resolves within this same commit.
    // Returns the number of backdated completed postings written.
    private fun seedWarningForecast(
        userId: UUID,
        accountHolderId: UUID,
        accountId: UUID,
        openingBalanceMinor: Long,
        initiatedById: UUID,
        now: Instant,
        uuids: UuidGenerator?,
    ): Int {
        val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        val monthStart = today.withDayOfMonth(1)

        val payeeIds = ForecastSeed.PAYEES.map { payee ->
            instrumentSeedHelper.insertPayee(
                accountHolderId = accountHolderId,
                name = payee.name,
                sortCode = payee.clearing.sortCode,
                accountNumber = payee.clearing.accountNumber,
                nickname = payee.nickname,
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
        }

        var completedMagnitude = 0L
        var completedPostings = 0

        ForecastSeed.COMPLETED_STANDING_ORDERS.forEach { order ->
            val payee = ForecastSeed.PAYEES[order.payeeIndex]
            val occurrenceDate = maxOf(today.plusDays(order.daysFromToday), monthStart)
            instrumentSeedHelper.insertStandingOrder(
                accountId = accountId,
                payeeId = payeeIds[order.payeeIndex],
                payeeName = payee.name,
                payeeSortCode = payee.clearing.sortCode,
                payeeAccountNumber = payee.clearing.accountNumber,
                reference = order.reference,
                amountMinorUnits = order.amountMinor,
                frequency = Frequency.MONTHLY,
                startDate = occurrenceDate,
                nextPaymentAt = occurrenceAfterToday(occurrenceDate, today),
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
            postCompletedOccurrence(accountId, payee.name, order.amountMinor, order.description, order.postingType, occurrenceDate, initiatedById, uuids)
            completedMagnitude += order.amountMinor
            completedPostings++
        }

        ForecastSeed.COMPLETED_DIRECT_DEBITS.forEach { mandate ->
            val occurrenceDate = maxOf(today.plusDays(mandate.daysFromToday), monthStart)
            instrumentSeedHelper.insertDirectDebit(
                accountId = accountId,
                creditorName = mandate.creditorName,
                creditorSortCode = mandate.creditor.sortCode,
                creditorAccountNumber = mandate.creditor.accountNumber,
                reference = mandate.reference,
                amountMinorUnits = mandate.amountMinor,
                frequency = Frequency.MONTHLY,
                startDate = occurrenceDate,
                nextCollectionAt = occurrenceAfterToday(occurrenceDate, today),
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
            postCompletedOccurrence(accountId, mandate.creditorName, mandate.amountMinor, mandate.description, "DIRECT_DEBIT_COLLECTION", occurrenceDate, initiatedById, uuids)
            completedMagnitude += mandate.amountMinor
            completedPostings++
        }

        ForecastSeed.EXPECTED_DIRECT_DEBITS.forEach { mandate ->
            val startDate = today.plusDays(mandate.daysFromToday)
            instrumentSeedHelper.insertDirectDebit(
                accountId = accountId,
                creditorName = mandate.creditorName,
                creditorSortCode = mandate.creditor.sortCode,
                creditorAccountNumber = mandate.creditor.accountNumber,
                reference = mandate.reference,
                amountMinorUnits = mandate.amountMinor,
                frequency = Frequency.MONTHLY,
                startDate = startDate,
                nextCollectionAt = startDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
                createdAt = now,
                createdBy = userId,
                idGenerator = uuids,
            )
        }

        val availableBalance = openingBalanceMinor - completedMagnitude
        val rent = ForecastSeed.EXPECTED_RENT
        val rentAmount = availableBalance + ForecastSeed.OVERSHOOT_MINOR - ForecastSeed.EXPECTED_FIXED_MAGNITUDE_MINOR
        val rentPayee = ForecastSeed.PAYEES[rent.payeeIndex]
        val rentStartDate = today.plusDays(rent.daysFromToday)
        instrumentSeedHelper.insertStandingOrder(
            accountId = accountId,
            payeeId = payeeIds[rent.payeeIndex],
            payeeName = rentPayee.name,
            payeeSortCode = rentPayee.clearing.sortCode,
            payeeAccountNumber = rentPayee.clearing.accountNumber,
            reference = rent.reference,
            amountMinorUnits = rentAmount,
            frequency = Frequency.MONTHLY,
            startDate = rentStartDate,
            nextPaymentAt = rentStartDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            createdAt = now,
            createdBy = userId,
            idGenerator = uuids,
        )

        return completedPostings
    }

    // A completed occurrence's anchor stays in the past so the forecast still projects it into
    // this month's Completed list (BalanceForecastAggregator reads start_date + frequency, never
    // the next date), but the schedule itself must already point past today. Leaving next_*_at on
    // the occurrence that has been and gone leaves an ACTIVE, past-due instrument that the
    // per-minute executor/requester fires within the minute — paying an occurrence the seeder has
    // already posted a backdated ledger entry for, and drifting the available balance the
    // OVERSHOOT arithmetic is sized from. Same rhythm-continuing rule the canonical seeder uses.
    private fun occurrenceAfterToday(anchor: LocalDate, today: LocalDate): Instant =
        OccurrenceSchedule.nextStrictlyAfter(anchor, Frequency.MONTHLY, today)
            .atStartOfDay(ZoneOffset.UTC).toInstant()

    private fun postCompletedOccurrence(
        accountId: UUID,
        counterpartyName: String,
        amountMinor: Long,
        description: String,
        type: String,
        occurrenceDate: LocalDate,
        initiatedById: UUID,
        uuids: UuidGenerator?,
    ) {
        ledgerSeedHelper.postBalancedPair(
            postedAt = occurrenceDate.atStartOfDay(ZoneOffset.UTC).toInstant(),
            currentSideAccountId = accountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = counterpartyName,
            amountOnCurrentSide = -amountMinor,
            description = description,
            type = type,
            initiatedById = initiatedById,
            idGenerator = uuids,
        )
    }

    // Seed-derived UUID stream for the canonical demo path: the same seed yields
    // the same instrument / transaction UUIDs on every reset. Seeded off the
    // bitwise-inverted seed so the id stream and the amount stream (Random(seed))
    // don't share a sequence. Not RFC-4122-versioned — Postgres `uuid` stores any
    // 128-bit value, and the seeder never round-trips these through a UUID version
    // check.
    private fun deterministicUuidGenerator(seed: Long): UuidGenerator {
        val idRandom = Random(seed.inv())
        return UuidGenerator { UUID(idRandom.nextLong(), idRandom.nextLong()) }
    }

    private fun logAndReturn(result: SeedResult, email: String, startedAt: Long): SeedResult {
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000
        val outcome = when (result) {
            is SeedResult.Seeded -> "seeded"
            is SeedResult.Noop -> "noop"
            is SeedResult.UserNotFound -> "user_not_found"
            is SeedResult.NoActiveCurrentAccount -> "no_active_current_account"
        }
        val userId = when (result) {
            is SeedResult.Seeded -> result.userId
            is SeedResult.Noop -> result.userId
            is SeedResult.NoActiveCurrentAccount -> result.userId
            is SeedResult.UserNotFound -> null
        }
        val accountId = when (result) {
            is SeedResult.Seeded -> result.accountId
            is SeedResult.Noop -> result.accountId
            else -> null
        }
        val transactionCount = when (result) {
            is SeedResult.Seeded -> result.transactionCount
            is SeedResult.Noop -> result.existingTransactionCount
            else -> 0
        }
        val openingBalanceMinor = when (result) {
            is SeedResult.Seeded -> result.openingBalanceMinor
            else -> 0L
        }
        log.info(
            "seeder.registration.seed",
            kv("email", email),
            kv("userId", userId?.toString()),
            kv("accountId", accountId?.toString()),
            kv("transactionCount", transactionCount),
            kv("openingBalanceMinor", openingBalanceMinor),
            kv("durationMs", durationMs),
            kv("outcome", outcome),
        )
        return result
    }

    private data class SpendingPosting(
        val daysAgo: Long,
        val counterpartyName: String,
        val description: String,
        val type: String,
        val amount: Long,
    )

    private data class GeneralSpending(
        val merchantName: String,
        val description: String,
        val type: String,
    )

    companion object {
        const val OPENING_MIN_MINOR: Long = 50_000L      // £500
        const val OPENING_MAX_MINOR: Long = 1_500_000L   // £15,000
        const val TX_COUNT_MIN: Int = 20
        const val TX_COUNT_MAX: Int = 30
        const val SPENDING_MIN_MINOR: Long = 100L        // £1
        const val SPENDING_MAX_MINOR: Long = 15_000L     // £150

        // Subset of UkMerchantsSeed strings suitable for daily-spending rotation.
        // ACME_SALARY is reserved for the single residual-absorbing credit.
        private val SPENDING_NARRATIVES: List<String> = listOf(
            UkMerchantsSeed.TESCO_WEEKLY,
            UkMerchantsSeed.TESCO_TOPUP,
            UkMerchantsSeed.TFL_COMMUTE,
            UkMerchantsSeed.PRET,
            UkMerchantsSeed.OCTOPUS_DD,
            UkMerchantsSeed.SPOTIFY_DD,
            UkMerchantsSeed.COUNCIL_TAX_DD,
            UkMerchantsSeed.CC_PAYMENT,
        )

        private val GENERAL_SPENDINGS: List<GeneralSpending> = listOf(
            GeneralSpending("Cash Machine", UkMerchantsSeed.CASH_WITHDRAWAL, "ATM_WITHDRAWAL"),
            GeneralSpending("Corner Shop", UkMerchantsSeed.CORNER_SHOP, "CARD_PAYMENT"),
            GeneralSpending("J Patel", UkMerchantsSeed.J_PATEL, "CARD_PAYMENT"),
        )

        val GENERAL_SPENDING_COUNT: Int = GENERAL_SPENDINGS.size
    }
}
