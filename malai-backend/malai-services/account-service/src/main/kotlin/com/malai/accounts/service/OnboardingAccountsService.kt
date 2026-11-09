package com.malai.accounts.service

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.identifier.AccountIdentifierFactory
import com.malai.accounts.model.CreateOnboardingAccountRequest
import com.malai.accounts.model.CreateOnboardingAccountResponse
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.client.malai.card.CardClient
import com.malai.client.malai.card.model.CardVariant.DEBIT
import com.malai.client.malai.card.model.IssueCardRequest
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.model.LedgerAccountKind.CUSTOMER
import com.malai.client.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import com.malai.client.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

/**
 * Backs the service-to-service onboarding endpoints in account-service. Mirrors the
 * pattern in account-holder-service's [com.malai.accountholder.service.OnboardingService] —
 * primary account is inserted as `PENDING`, flipped to `ACTIVE` on confirm-register.
 *
 * D-2.5: no balance row is created here — balance projects from `ledger_entries` so a
 * brand-new account simply reads `0` until its first posting.
 */
@Service
class OnboardingAccountsService(
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val accountIdentifierFactory: AccountIdentifierFactory,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
    private val auditWriter: AuditWriter,
    private val ledgerClient: LedgerClient,
    private val cardClient: CardClient,
    private val accountsServiceTokenClient: AccountsServiceTokenClient,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @Transactional
    fun createAccount(request: CreateOnboardingAccountRequest): CreateOnboardingAccountResponse {
        val entity = AccountEntity(
            accountId = uuidGenerator.generate(),
            accountType = AccountType.PRIMARY,
            defaultCategory = uuidGenerator.generate(),
            currency = Currency.valueOf(request.currency!!),
            createdAt = timeService.now(),
            name = request.name!!,
            userId = request.userId!!,
            accountHolderId = request.accountHolderId!!,
            status = OnboardingStatus.PENDING,
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

        // Account numbers come from the `account_number_seq` Postgres sequence (see
        // [com.malai.accounts.identifier.AccountNumberGenerator]), so they're unique by
        // construction — no application-level collision handling. The DB unique constraint
        // on `(identifier_type, account_identifier)` remains as a structural invariant; if
        // it ever fires that's a corrupted DB or a manual data fix gone wrong, and a
        // generic 500 is the right outcome.
        accountIdentifierRepository.saveAll(accountIdentifierFactory.identifiersFor(saved.accountId))

        cardClient.issueCard(
            accountsServiceTokenClient.getToken(),
            IssueCardRequest(
                accountId = saved.accountId,
                accountHolderId = saved.accountHolderId,
                cardholderName = saved.name,
                variant = DEBIT,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = request.userId,
                actorType = ActorType.SYSTEM,
                action = "ACCOUNT_ONBOARDED",
                resourceType = "Account",
                resourceId = saved.accountId.toString(),
                afterState = mapOf(
                    "accountType" to saved.accountType.name,
                    "currency" to saved.currency.name,
                    "accountHolderId" to saved.accountHolderId.toString(),
                    "status" to saved.status.name,
                ),
            )
        )

        log.info("onboarding: created account ${entity.accountId} (PENDING)")
        return CreateOnboardingAccountResponse(entity.accountId)
    }

    @Transactional
    fun activateAccountsForUser(userId: UUID) {
        val activated = accountRepository.findByUserId(userId).map { account ->
            accountRepository.save(account.copy(status = ACTIVE))
        }
        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.SYSTEM,
                action = "ACCOUNTS_ACTIVATED",
                resourceType = "User",
                resourceId = userId.toString(),
                afterState = mapOf("activatedAccountIds" to activated.map { it.accountId.toString() }),
            )
        )
        log.info("onboarding: activated accounts for user $userId")
    }
}
