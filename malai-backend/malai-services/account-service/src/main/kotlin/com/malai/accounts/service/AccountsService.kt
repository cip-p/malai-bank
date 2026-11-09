package com.malai.accounts.service

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountIdentifierEntity
import com.malai.accounts.entity.AccountType.ADDITIONAL
import com.malai.accounts.entity.AccountType.CREDIT_CARD
import com.malai.accounts.entity.AccountType.PRIMARY
import com.malai.accounts.entity.AccountType.SUSPENSE
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.exception.AccountNotFoundException
import com.malai.accounts.identifier.AccountIdentifierFactory
import com.malai.accounts.model.*
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.client.malai.insights.InsightsClient
import com.malai.client.malai.insights.model.BalanceForecastRequest
import com.malai.client.malai.insights.model.CategoriseEntriesRequest
import com.malai.client.malai.insights.model.Category
import com.malai.client.malai.insights.model.CategoryOverrideRequest
import com.malai.client.malai.insights.model.EntryFact
import com.malai.client.malai.insights.model.ForecastInstrumentFact
import com.malai.client.malai.insights.model.SpendingBreakdownRequest
import com.malai.client.malai.directdebit.DirectDebitReadClient
import com.malai.client.malai.card.CardClient
import com.malai.client.malai.card.model.AccountAuthorisationResponse
import com.malai.client.malai.card.model.AuthorisationStatus
import com.malai.client.malai.card.model.CardVariant.CREDIT
import com.malai.client.malai.card.model.CardVariant.DEBIT
import com.malai.client.malai.card.model.IssueCardRequest
import com.malai.client.malai.card.model.IssueCardResponse
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.standingorder.StandingOrderReadClient
import com.malai.client.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.client.malai.ledger.model.LedgerEntryProjection
import com.malai.client.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.client.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.client.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.CREDIT_CARD_ACCOUNT_ALREADY_EXISTS
import com.malai.commons.exception.MalaiCode.INSIGHTS_UNAVAILABLE
import com.malai.commons.exception.MalaiCode.INVALID_CATEGORY
import com.malai.commons.exception.MalaiCode.PRIMARY_ACCOUNT_ALREADY_EXISTS
import com.malai.commons.exception.MalaiCode.PRIMARY_ACCOUNT_MUST_EXIST
import com.malai.commons.exception.ServiceUnavailableException
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset.UTC
import java.util.*

/**
 * The internet-facing balance/transactions/statement/CoF reads are thin composers over the
 * ledger: account-service owns account ownership + metadata (accountUid, currency, name), the
 * ledger owns the money. Each read verifies ownership (scoping the resource to the authenticated
 * account holder), then wraps a ledger projection with that metadata. The ledger is reached via
 * [ledgerClient], authenticated with a Cognito service token from [accountsServiceTokenClient].
 */
@Service
class AccountsService(
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountIdentifierFactory: AccountIdentifierFactory,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
    private val auditWriter: AuditWriter,
    private val ledgerClient: LedgerClient,
    private val cardClient: CardClient,
    private val insightsClient: InsightsClient,
    private val standingOrderReadClient: StandingOrderReadClient,
    private val directDebitReadClient: DirectDebitReadClient,
    private val accountsServiceTokenClient: AccountsServiceTokenClient,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun getAccounts(accountHolderId: UUID): AccountsResponse {
        val accounts = accountRepository.findByAccountHolderId(accountHolderId)
            .filter { it.accountType != SUSPENSE }
            .map { toResponse(it) }
        return AccountsResponse(accounts)
    }

    fun getBalance(accountUid: UUID, accountHolderId: UUID): BalanceResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        val balances = ledgerClient.getBalances(accountsServiceTokenClient.getToken(), accountUid)
        return BalanceResponse(
            currency = account.currency,
            amountMinorUnits = balances.ledgerBalanceMinorUnits,
            availableBalanceMinorUnits = balances.availableBalanceMinorUnits,
            heldAmountMinorUnits = balances.heldAmountMinorUnits,
            pendingMinorUnits = null,
        )
    }

    fun getIdentifiers(accountUid: UUID, accountHolderId: UUID): AccountIdentifiersResponse {
        verifyAccountOwnership(accountUid, accountHolderId)

        val identifiers = accountIdentifierRepository.findByAccountId(accountUid)
            .sortedBy { it.identifierType.ordinal }
        val sortCode = identifiers.single { it.identifierType == SORT_CODE }
        val ibanBic = identifiers.singleOrNull { it.identifierType == IBAN_BIC }

        return AccountIdentifiersResponse(
            accountIdentifier = sortCode.accountIdentifier,
            bankIdentifier = sortCode.bankIdentifier,
            iban = ibanBic?.accountIdentifier,
            bic = ibanBic?.bankIdentifier,
            accountIdentifiers = identifiers.map { toIdentifierResponse(it) }
        )
    }

    fun getTransactions(accountUid: UUID, accountHolderId: UUID): TransactionsResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        val entries = ledgerClient.listEntries(accountsServiceTokenClient.getToken(), accountUid)
        val categoryByEntryId = categorise(entries.entries)
        val ledgerItems = entries.entries.map { toResponse(it, account.currency, categoryByEntryId[it.entryId]) }
        val cardItems = cardAuthorisationItems(accountUid, account.currency)
        return TransactionsResponse(
            accountUid = account.accountId,
            transactions = mergeNewestFirst(ledgerItems, cardItems),
        )
    }

    private fun cardAuthorisationItems(accountId: UUID, currency: Currency): List<TransactionItemResponse> =
        try {
            cardClient.listAccountAuthorisations(accountsServiceTokenClient.getToken(), accountId)
                .authorisations.map { toCardFeedItem(it, currency) }
        } catch (ex: Exception) {
            log.warn("card authorisations unavailable; returning feed without pending/declined card rows (fail open)", ex)
            emptyList()
        }

    private fun mergeNewestFirst(
        ledgerItems: List<TransactionItemResponse>,
        cardItems: List<TransactionItemResponse>,
    ): List<TransactionItemResponse> {
        if (cardItems.isEmpty()) return ledgerItems
        val merged = (ledgerItems + cardItems).sortedByDescending { it.postedAt }.toMutableList()
        var carriedBalance = ledgerItems.lastOrNull()?.runningBalanceMinorUnits ?: 0L
        for (i in merged.indices.reversed()) {
            val item = merged[i]
            if (item.status == null) carriedBalance = item.runningBalanceMinorUnits
            else merged[i] = item.copy(runningBalanceMinorUnits = carriedBalance)
        }
        return merged
    }

    private fun categorise(entries: List<LedgerEntryProjection>): Map<UUID, String> {
        if (entries.isEmpty()) return emptyMap()
        return try {
            insightsClient.categorise(
                accountsServiceTokenClient.getToken(),
                CategoriseEntriesRequest(
                    entries = entries.map {
                        EntryFact(
                            entryId = it.entryId,
                            amountMinorUnits = it.amountMinorUnits,
                            merchantName = it.merchantName,
                            description = it.description,
                            transactionType = it.transactionType,
                        )
                    },
                ),
            ).categorisations.associate { it.entryId to it.category.name }
        } catch (ex: Exception) {
            log.warn("insights categorisation unavailable; returning feed without categories (fail open)", ex)
            emptyMap()
        }
    }

    fun getStatement(accountUid: UUID, accountHolderId: UUID, yyyymm: String): StatementResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        val period = parseStatementPeriod(yyyymm)

        val entries = ledgerClient.listEntries(
            accountsServiceTokenClient.getToken(),
            accountUid,
            periodStart = period.start.toString(),
            periodEnd = period.end.toString(),
        )

        val closingBalance = entries.closingBalanceMinorUnits ?: 0
        val figures = if (account.accountType == CREDIT_CARD) {
            creditStatementFigures(closingBalance, YearMonth.parse(yyyymm))
        } else {
            null
        }

        return StatementResponse(
            accountUid = account.accountId,
            periodStart = period.start,
            periodEnd = period.end,
            currency = account.currency,
            openingBalanceMinorUnits = entries.openingBalanceMinorUnits ?: 0,
            closingBalanceMinorUnits = closingBalance,
            transactions = entries.entries.map { toResponse(it, account.currency, it.category) },
            statementBalanceMinorUnits = figures?.statementBalanceMinorUnits,
            minimumPaymentMinorUnits = figures?.minimumPaymentMinorUnits,
            dueDate = figures?.dueDate,
        )
    }

    fun getSpending(accountUid: UUID, accountHolderId: UUID, month: String): SpendingBreakdownResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        val period = parseStatementPeriod(month)

        val entries = ledgerClient.listEntries(
            accountsServiceTokenClient.getToken(),
            accountUid,
            periodStart = period.start.toString(),
            periodEnd = period.end.toString(),
        )

        val breakdown = try {
            insightsClient.spendingBreakdown(
                accountsServiceTokenClient.getToken(),
                SpendingBreakdownRequest(
                    month = month,
                    entries = entries.entries.map {
                        EntryFact(
                            entryId = it.entryId,
                            amountMinorUnits = it.amountMinorUnits,
                            merchantName = it.merchantName,
                            description = it.description,
                            transactionType = it.transactionType,
                        )
                    },
                ),
            )
        } catch (ex: Exception) {
            log.warn("insights spending-breakdown unavailable; failing closed with 503", ex)
            throw ServiceUnavailableException(INSIGHTS_UNAVAILABLE)
        }

        return SpendingBreakdownResponse(
            month = month,
            currency = account.currency,
            categories = breakdown.categories.map {
                SpendingCategoryTotalResponse(
                    category = it.category.name,
                    totalMinorUnits = it.totalMinorUnits,
                    transactionCount = it.transactionCount,
                )
            },
            totalMinorUnits = breakdown.totalMinorUnits,
        )
    }

    fun getForecast(accountUid: UUID, accountHolderId: UUID): BalanceForecastResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        val asOfDate = LocalDate.ofInstant(timeService.now(), UTC)

        val forecast = try {
            val serviceToken = accountsServiceTokenClient.getToken()
            val availableBalance = ledgerClient.getBalances(serviceToken, accountUid).availableBalanceMinorUnits
            val standingOrders = standingOrderReadClient.getActiveStandingOrders(serviceToken, accountUid).standingOrders
            val directDebits = directDebitReadClient.getActiveDirectDebits(serviceToken, accountUid).directDebits

            val standingOrderInstruments = standingOrders.map {
                ForecastInstrumentFact(
                    instrumentId = it.standingOrderId,
                    type = "STANDING_ORDER",
                    counterpartyName = it.payeeName,
                    amountMinorUnits = it.amountMinorUnits,
                    frequency = it.frequency,
                    startDate = it.startDate,
                    status = it.status,
                )
            }
            val directDebitInstruments = directDebits.mapNotNull { dd ->
                dd.amountMinorUnits?.let { amount ->
                    ForecastInstrumentFact(
                        instrumentId = dd.directDebitId,
                        type = "DIRECT_DEBIT",
                        counterpartyName = dd.creditorName,
                        amountMinorUnits = amount,
                        frequency = dd.frequency,
                        startDate = dd.startDate,
                        status = dd.status,
                    )
                }
            }

            insightsClient.balanceForecast(
                serviceToken,
                BalanceForecastRequest(
                    asOfDate = asOfDate.toString(),
                    availableBalanceMinorUnits = availableBalance,
                    currency = account.currency.name,
                    instruments = standingOrderInstruments + directDebitInstruments,
                ),
            )
        } catch (ex: Exception) {
            log.warn("balance-forecast fan-out unavailable; failing closed with 503", ex)
            throw ServiceUnavailableException(INSIGHTS_UNAVAILABLE)
        }

        return BalanceForecastResponse(
            accountUid = accountUid,
            month = forecast.month,
            windowStart = forecast.windowStart,
            windowEnd = forecast.windowEnd,
            asOfDate = forecast.asOfDate,
            currency = account.currency,
            availableBalanceMinorUnits = forecast.availableBalanceMinorUnits,
            completedTotalMinorUnits = forecast.completedTotalMinorUnits,
            expectedTotalMinorUnits = forecast.expectedTotalMinorUnits,
            forecastBalanceMinorUnits = forecast.forecastBalanceMinorUnits,
            completed = forecast.completed.map {
                ForecastOccurrenceResponse(
                    date = it.date,
                    counterpartyName = it.counterpartyName,
                    amountMinorUnits = it.amountMinorUnits,
                    type = it.type,
                    instrumentId = it.instrumentId,
                )
            },
            expected = forecast.expected.map {
                ForecastOccurrenceResponse(
                    date = it.date,
                    counterpartyName = it.counterpartyName,
                    amountMinorUnits = it.amountMinorUnits,
                    type = it.type,
                    instrumentId = it.instrumentId,
                )
            },
        )
    }

    fun overrideCategory(
        accountUid: UUID,
        entryId: UUID,
        category: String,
        userId: UUID,
        accountHolderId: UUID,
    ) {
        verifyAccountOwnership(accountUid, accountHolderId)
        val resolved = parseCategory(category)

        insightsClient.putOverride(
            accountsServiceTokenClient.getToken(),
            entryId,
            CategoryOverrideRequest(category = resolved),
        )

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "CATEGORY_OVERRIDDEN",
                resourceType = "LedgerEntry",
                resourceId = entryId.toString(),
                afterState = mapOf(
                    "category" to resolved.name,
                    "accountUid" to accountUid.toString(),
                ),
            )
        )
    }

    fun clearCategoryOverride(
        accountUid: UUID,
        entryId: UUID,
        userId: UUID,
        accountHolderId: UUID,
    ) {
        verifyAccountOwnership(accountUid, accountHolderId)

        insightsClient.deleteOverride(accountsServiceTokenClient.getToken(), entryId)

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "CATEGORY_OVERRIDE_CLEARED",
                resourceType = "LedgerEntry",
                resourceId = entryId.toString(),
                afterState = mapOf("accountUid" to accountUid.toString()),
            )
        )
    }

    private fun parseCategory(value: String): Category =
        enumValues<Category>().firstOrNull { it.name == value }
            ?: throw BadRequestException(INVALID_CATEGORY)

    private data class StatementPeriod(val start: Instant, val end: Instant)

    private fun parseStatementPeriod(yyyymm: String): StatementPeriod {
        val ym = java.time.YearMonth.parse(yyyymm) // format `YYYY-MM`; throws DateTimeParseException on malformed input
        val start = ym.atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
        return StatementPeriod(start, end)
    }

    private fun toResponse(entry: LedgerEntryProjection, currency: Currency, category: String?) = TransactionItemResponse(
        entryId = entry.entryId,
        transactionId = entry.transactionId,
        postedAt = Instant.parse(entry.postedAt),
        currency = currency,
        amountMinorUnits = entry.amountMinorUnits,
        runningBalanceMinorUnits = entry.runningBalanceMinorUnits,
        description = entry.description,
        counterpartyId = entry.counterpartyId,
        merchantName = entry.merchantName,
        category = category,
    )

    private fun toCardFeedItem(authorisation: AccountAuthorisationResponse, currency: Currency) = TransactionItemResponse(
        entryId = authorisation.authorisationId,
        transactionId = authorisation.authorisationId,
        postedAt = Instant.parse(authorisation.createdAt),
        currency = currency,
        amountMinorUnits = -authorisation.amountMinorUnits,
        runningBalanceMinorUnits = 0L,
        merchantName = authorisation.merchantName,
        status = when (authorisation.status) {
            AuthorisationStatus.AUTHORISED -> TransactionStatus.PENDING
            AuthorisationStatus.DECLINED -> TransactionStatus.DECLINED
        },
        declineReason = authorisation.declineReason?.name,
        cardId = authorisation.cardId,
    )

    fun getConfirmationOfFunds(
        accountUid: UUID,
        accountHolderId: UUID,
        targetAmountInMinorUnits: Long
    ): ConfirmationOfFundsResponse {
        verifyAccountOwnership(accountUid, accountHolderId)
        val available = ledgerClient.getBalances(accountsServiceTokenClient.getToken(), accountUid)
            .availableBalanceMinorUnits

        return ConfirmationOfFundsResponse(
            requestedAmountAvailableToSpend = targetAmountInMinorUnits <= available,
            accountWouldBeInOverdraftIfRequestedAmountSpent = targetAmountInMinorUnits > available,
        )
    }

    fun getCreditSummary(accountUid: UUID, accountHolderId: UUID): CreditSummaryResponse {
        val account = verifyAccountOwnership(accountUid, accountHolderId)
        if (account.accountType != CREDIT_CARD) throw AccountNotFoundException()

        val balances = ledgerClient.getBalances(accountsServiceTokenClient.getToken(), accountUid)
        val figures = creditStatementFigures(balances.ledgerBalanceMinorUnits, currentCycleMonth())
        return CreditSummaryResponse(
            currency = account.currency,
            creditLimitMinorUnits = CREDIT_CARD_LIMIT_MINOR_UNITS,
            owedMinorUnits = -balances.ledgerBalanceMinorUnits,
            availableCreditMinorUnits = CREDIT_CARD_LIMIT_MINOR_UNITS + balances.availableBalanceMinorUnits,
            statementBalanceMinorUnits = figures?.statementBalanceMinorUnits,
            minimumPaymentMinorUnits = figures?.minimumPaymentMinorUnits,
            dueDate = figures?.dueDate,
        )
    }

    private data class CreditStatementFigures(
        val statementBalanceMinorUnits: Long,
        val minimumPaymentMinorUnits: Long,
        val dueDate: LocalDate,
    )

    private fun currentCycleMonth(): YearMonth = YearMonth.from(timeService.now().atZone(UTC))

    private fun creditStatementFigures(closingBalanceMinorUnits: Long, cycleMonth: YearMonth): CreditStatementFigures? =
        try {
            cardClient.getStatementFigures(
                accountsServiceTokenClient.getToken(),
                closingBalanceMinorUnits,
                cycleMonth.toString(),
            ).let {
                CreditStatementFigures(
                    statementBalanceMinorUnits = it.statementBalanceMinorUnits,
                    minimumPaymentMinorUnits = it.minimumPaymentMinorUnits,
                    dueDate = LocalDate.parse(it.dueDate),
                )
            }
        } catch (ex: Exception) {
            log.warn("credit statement figures unavailable; composing without them (fail open)", ex)
            null
        }

    @Transactional
    fun applyForCreditCard(userId: UUID, accountHolderId: UUID): CreditCardApplicationResponse {
        if (accountRepository.existsByAccountHolderIdAndAccountType(accountHolderId, CREDIT_CARD)) {
            throw ConflictErrorException(CREDIT_CARD_ACCOUNT_ALREADY_EXISTS)
        }

        val account = accountRepository.save(
            AccountEntity(
                accountId = uuidGenerator.generate(),
                accountType = CREDIT_CARD,
                defaultCategory = uuidGenerator.generate(),
                currency = Currency.GBP,
                createdAt = timeService.now(),
                name = CREDIT_CARD_ACCOUNT_NAME,
                userId = userId,
                accountHolderId = accountHolderId,
                status = ACTIVE,
            )
        )

        ledgerClient.registerLedgerAccount(
            accountsServiceTokenClient.getToken(),
            RegisterLedgerAccountRequest(
                accountId = account.accountId,
                currency = account.currency.name,
                kind = CUSTOMER,
                guardPolicy = FLOORED,
                floorMinorUnits = CREDIT_CARD_FLOOR_MINOR_UNITS,
            ),
        )

        accountIdentifierRepository.saveAll(accountIdentifierFactory.identifiersFor(account.accountId))

        val card = cardClient.issueCard(
            accountsServiceTokenClient.getToken(),
            IssueCardRequest(
                accountId = account.accountId,
                accountHolderId = accountHolderId,
                cardholderName = account.name,
                variant = CREDIT,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "CREDIT_CARD_APPLICATION_APPROVED",
                resourceType = "Account",
                resourceId = account.accountId.toString(),
                afterState = mapOf(
                    "accountType" to account.accountType.name,
                    "currency" to account.currency.name,
                    "accountHolderId" to accountHolderId.toString(),
                    "creditLimitMinorUnits" to CREDIT_CARD_LIMIT_MINOR_UNITS,
                    "cardId" to card.cardId.toString(),
                ),
            )
        )

        return CreditCardApplicationResponse(
            account = toResponse(account),
            card = toIssuedCardResponse(card),
        )
    }

    @Transactional
    fun createAccount(request: CreateAccountRequest, userId: UUID, accountHolderId: UUID): AccountResponse {
        val primaryExists = accountRepository.existsByAccountHolderIdAndAccountType(accountHolderId, PRIMARY)

        if (request.accountType == PRIMARY && primaryExists) {
            throw ConflictErrorException(PRIMARY_ACCOUNT_ALREADY_EXISTS)
        }
        if (request.accountType != PRIMARY && !primaryExists) {
            throw ConflictErrorException(PRIMARY_ACCOUNT_MUST_EXIST)
        }

        val entity = AccountEntity(
            accountId = uuidGenerator.generate(),
            accountType = request.accountType,
            defaultCategory = uuidGenerator.generate(),
            currency = request.currency,
            createdAt = timeService.now(),
            name = request.name,
            userId = userId,
            accountHolderId = accountHolderId,
            status = ACTIVE,
        )

        val saved = accountRepository.save(entity)

        ledgerClient.registerLedgerAccount(
            accountsServiceTokenClient.getToken(),
            RegisterLedgerAccountRequest(
                accountId = saved.accountId,
                currency = saved.currency.name,
                kind = CUSTOMER,
                guardPolicy = NO_OVERDRAFT,
            ),
        )

        accountIdentifierRepository.saveAll(accountIdentifierFactory.identifiersFor(saved.accountId))

        issueDebitCardIfEligible(saved)

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "ACCOUNT_CREATED",
                resourceType = "Account",
                resourceId = saved.accountId.toString(),
                afterState = mapOf(
                    "accountType" to saved.accountType.name,
                    "currency" to saved.currency.name,
                    "accountHolderId" to saved.accountHolderId.toString(),
                ),
            )
        )

        return toResponse(saved)
    }

    private fun issueDebitCardIfEligible(account: AccountEntity) {
        if (account.accountType !in DEBIT_CARD_ACCOUNT_TYPES) return
        cardClient.issueCard(
            accountsServiceTokenClient.getToken(),
            IssueCardRequest(
                accountId = account.accountId,
                accountHolderId = account.accountHolderId,
                cardholderName = account.name,
                variant = DEBIT,
            ),
        )
    }

    private fun toIssuedCardResponse(card: IssueCardResponse) = IssuedCardResponse(
        cardId = card.cardId,
        accountId = card.accountId,
        maskedPan = card.maskedPan,
        variant = card.variant.name,
        status = card.status.name,
        expiry = card.expiry,
    )

    private companion object {
        val DEBIT_CARD_ACCOUNT_TYPES = setOf(PRIMARY, ADDITIONAL)
        const val CREDIT_CARD_LIMIT_MINOR_UNITS = 250_000L
        const val CREDIT_CARD_FLOOR_MINOR_UNITS = -250_000L
        const val CREDIT_CARD_ACCOUNT_NAME = "Credit Card"
    }

    private fun verifyAccountOwnership(accountUid: UUID, accountHolderId: UUID): AccountEntity =
        accountRepository.findByAccountIdAndAccountHolderId(accountUid, accountHolderId)
            ?: throw AccountNotFoundException()

    private fun toResponse(entity: AccountEntity) = AccountResponse(
        accountUid = entity.accountId,
        accountType = entity.accountType.name,
        defaultCategory = entity.defaultCategory,
        currency = entity.currency,
        createdAt = entity.createdAt,
        name = entity.name
    )

    private fun toIdentifierResponse(entity: AccountIdentifierEntity) = AccountIdentifierResponse(
        identifierType = entity.identifierType.name,
        bankIdentifier = entity.bankIdentifier,
        accountIdentifier = entity.accountIdentifier
    )
}
