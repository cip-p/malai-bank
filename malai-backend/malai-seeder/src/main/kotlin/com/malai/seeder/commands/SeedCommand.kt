package com.malai.seeder.commands

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.AddressEntity
import com.malai.accountholder.entity.IndividualEntity
import com.malai.accountholder.repository.AccountHolderRepository
import com.malai.accountholder.repository.AddressRepository
import com.malai.accountholder.repository.IndividualRepository
import com.malai.accounts.entity.*
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.auth.repository.UserRepository
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.mdr.model.HumanDispositionKind
import com.malai.mdr.model.ModelDecisionOutcome
import com.malai.mdr.model.RecordHumanDispositionRequest
import com.malai.mdr.model.RecordModelDecisionRequest
import com.malai.mdr.model.RetrievedContextRef
import com.malai.mdr.service.MdrDecisionService
import com.malai.mdr.service.MdrDispositionService
import com.malai.registrationseeder.RegistrationSeederService
import com.malai.registrationseeder.SeedResult
import com.malai.registrationseeder.card.CardSeedHelper
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.registrationseeder.data.UkMerchantsSeed
import com.malai.registrationseeder.ledger.LedgerSeedHelper
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.seeder.cognito.SeederCognitoAdmin
import com.malai.seeder.data.AmlDemoSeed
import com.malai.seeder.data.BankingDaySeed
import com.malai.seeder.data.CustomerOnlySeed
import com.malai.seeder.data.ManagerOnlySeed
import com.malai.seeder.data.MdrDemoSeed
import com.malai.seeder.data.OpsReviewerSeed
import com.malai.seeder.data.SarahMitchellSeed
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import picocli.CommandLine.Command
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random

/**
 * Loads the canonical demo dataset: Sarah Mitchell (customer + admin, full
 * estate), the ops reviewer (admin only, no estate), the customer-only user
 * (customer only, minimal estate — the real-bank surface-gating persona) and
 * the manager-only user (manager only, no estate — the narrow-role gating
 * persona, ADR-0010).
 *
 * Cognito + the user / account-holder / individual / address / account
 * identities are created here, keyed on the fixed deterministic UUIDs the webapp
 * pins against. Each customer CURRENT account's transactions and instruments
 * (payees, standing orders, direct debits) are then delegated to [RegistrationSeederService]
 * — the SAME code path that runs for a real self-registered user via the
 * post-registration hook.
 *
 * Delegating is the point: the demo dataset can no longer drift from what
 * registration actually produces. When a later feature adds registration-time
 * data (spending insights next), a re-seed picks it up for free — there is no
 * parallel seed definition to keep in sync. The CLI drives the seeded
 * (deterministic) `seedFor(email, seed)` overload so Sarah reproduces
 * byte-for-byte on every `--reset`; a real user still gets a fresh random dataset.
 *
 * Sarah keeps a SAVINGS account as a seeder-only extra — registration onboards a
 * single CURRENT account, so one D-30 opening posting (£8400.00) is added here to
 * keep the multi-account dashboard demo intact.
 *
 * Cognito calls are emitted BEFORE the DB transaction opens so a failed admin
 * call doesn't roll back the user row's audit hash. The Cognito side is
 * idempotent (AdminCreateUser swallows UsernameExistsException) — see
 * [SeederCognitoAdmin].
 *
 * A single `@Transactional` wrap (on the picocli entrypoint) means the V5 deferred
 * `ledger_entries_sum_to_zero` trigger validates every pair at COMMIT and the
 * audit chain ends in a consistent state — including the rows
 * RegistrationSeederService writes, which join this transaction.
 */
@Component
@Command(name = "seed", description = ["Insert the canonical demo dataset (Sarah Mitchell, ops reviewer, customer-only user, manager-only user)."])
class SeedCommand(
    private val userRepository: UserRepository,
    private val accountHolderRepository: AccountHolderRepository,
    private val individualRepository: IndividualRepository,
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val addressRepository: AddressRepository,
    private val ledgerSeedHelper: LedgerSeedHelper,
    private val cardSeedHelper: CardSeedHelper,
    private val registrationSeederService: RegistrationSeederService,
    private val malaiStubClient: MalaiStubClient,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
    private val cognitoAdmin: SeederCognitoAdmin,
    private val jdbcTemplate: JdbcTemplate,
    private val mdrDecisionService: MdrDecisionService,
    private val mdrDispositionService: MdrDispositionService,
) : Runnable {

    private val log = LoggerFactory.getLogger(javaClass)

    private val verdictSerialiser = jacksonObjectMapper()

    // Resolved during seedCognito(): the Cognito `sub` for Sarah when the pool is
    // enabled, otherwise the static SarahMitchellSeed.USER_ID fallback for local-dev.
    // Every DB row that references the user identity reads from this — keying on
    // anything else means login (which carries the real sub) returns 8979.
    private lateinit var sarahUserId: UUID

    // Same resolution for the ops reviewer (OpsReviewerSeed.USER_ID fallback).
    private lateinit var opsUserId: UUID

    // Same resolution for the customer-only user (CustomerOnlySeed.USER_ID fallback).
    private lateinit var customerOnlyUserId: UUID

    // Same resolution for the manager-only user (ManagerOnlySeed.USER_ID fallback).
    private lateinit var managerUserId: UUID

    // Same resolution for each AML monitored customer, keyed on email (AmlDemoSeed fallbacks).
    private val monitoredUserIds = mutableMapOf<String, UUID>()

    // @Transactional goes on the picocli entrypoint, NOT on seedDatabase().
    //
    // PicocliSpringFactory resolves SeedCommand via the Spring context, so the
    // proxy is what picocli invokes — Transactional advice fires on this method.
    // seedDatabase() below is called via `this`, which bypasses the proxy; any
    // @Transactional on it would be silently ignored and each repository.save()
    // would commit on its own. The V5 `ledger_entries_sum_to_zero` deferred
    // trigger then rejects the first leg's commit (one row, sum != 0).
    //
    // RegistrationSeederService.seedFor is itself @Transactional, but it is called
    // as a proxied bean from inside this transaction, so it joins (REQUIRED) rather
    // than opening its own — every write still commits together here.
    //
    // The cognito calls running inside this tx are harmless: they don't touch
    // the DataSource, AWS SDK calls aren't enrolled in JDBC transactions, and
    // the seeder runs at human cadence (the Hikari connection it holds is fine).
    @Transactional
    override fun run() {
        seedCognito()
        seedDatabase()
        log.info("seed: complete")
    }

    private fun seedCognito() {
        log.info("seed: Cognito — create Sarah, set password, add to customer + admin groups")
        val cognitoSub = cognitoAdmin.adminCreateUser(SarahMitchellSeed.EMAIL, SarahMitchellSeed.FULL_NAME)
        cognitoAdmin.adminSetUserPassword(
            email = SarahMitchellSeed.EMAIL,
            password = SarahMitchellSeed.DEMO_PASSWORD,
            permanent = true,
        )
        cognitoAdmin.adminAddUserToGroup(SarahMitchellSeed.EMAIL, "customer")
        cognitoAdmin.adminAddUserToGroup(SarahMitchellSeed.EMAIL, "admin")
        sarahUserId = cognitoSub ?: SarahMitchellSeed.USER_ID

        log.info("seed: Cognito — create ops reviewer, set password, add to admin group")
        val opsSub = cognitoAdmin.adminCreateUser(OpsReviewerSeed.EMAIL, OpsReviewerSeed.FULL_NAME)
        cognitoAdmin.adminSetUserPassword(
            email = OpsReviewerSeed.EMAIL,
            password = OpsReviewerSeed.DEMO_PASSWORD,
            permanent = true,
        )
        cognitoAdmin.adminAddUserToGroup(OpsReviewerSeed.EMAIL, "admin")
        opsUserId = opsSub ?: OpsReviewerSeed.USER_ID

        // The customer-only user's exclusion from the demo all-hats grant is structural:
        // no admin group-add is ever emitted for this email.
        log.info("seed: Cognito — create customer-only user, set password, add to customer group only")
        val customerOnlySub = cognitoAdmin.adminCreateUser(CustomerOnlySeed.EMAIL, CustomerOnlySeed.FULL_NAME)
        cognitoAdmin.adminSetUserPassword(
            email = CustomerOnlySeed.EMAIL,
            password = CustomerOnlySeed.DEMO_PASSWORD,
            permanent = true,
        )
        cognitoAdmin.adminAddUserToGroup(CustomerOnlySeed.EMAIL, "customer")
        customerOnlyUserId = customerOnlySub ?: CustomerOnlySeed.USER_ID

        // The manager-only user's exclusion from the demo all-hats grant is structural:
        // neither an admin nor a customer group-add is ever emitted for this email. The
        // narrow role (ADR-0010) is only real if the admin API refuses this identity.
        log.info("seed: Cognito — create manager-only user, set password, add to manager group only")
        val managerSub = cognitoAdmin.adminCreateUser(ManagerOnlySeed.EMAIL, ManagerOnlySeed.FULL_NAME)
        cognitoAdmin.adminSetUserPassword(
            email = ManagerOnlySeed.EMAIL,
            password = ManagerOnlySeed.DEMO_PASSWORD,
            permanent = true,
        )
        cognitoAdmin.adminAddUserToGroup(ManagerOnlySeed.EMAIL, "manager")
        managerUserId = managerSub ?: ManagerOnlySeed.USER_ID

        // The AML museum's subjects are ordinary retail customers who can log in — that is what
        // makes the tipping-off invariant demonstrable rather than merely asserted. Customer group
        // only: being under investigation grants no staff surface.
        AmlDemoSeed.MONITORED_CUSTOMERS.forEach { customer ->
            log.info("seed: Cognito — create monitored customer {}, set password, add to customer group only", customer.email)
            val sub = cognitoAdmin.adminCreateUser(customer.email, customer.fullName)
            cognitoAdmin.adminSetUserPassword(
                email = customer.email,
                password = customer.demoPassword,
                permanent = true,
            )
            cognitoAdmin.adminAddUserToGroup(customer.email, "customer")
            monitoredUserIds[customer.email] = sub ?: customer.userId
        }
    }

    // Intentionally NOT @Transactional — see the docstring on `run()`.
    private fun seedDatabase() {
        val now = timeService.now()

        log.info("seed: user + account holder + individual")
        seedUserAndHolder(now)

        log.info("seed: ops reviewer (users row only — back-office staff, not a customer)")
        seedOpsReviewer(now)

        log.info("seed: customer-only user (customer group only — the real-bank surface-gating persona)")
        seedCustomerOnlyUser(now)

        log.info("seed: manager-only user (manager group only — the narrow-role gating persona)")
        seedManagerOnlyUser(now)

        log.info("seed: 2 user accounts (CURRENT, SAVINGS)")
        seedUserAccount(
            accountId = SarahMitchellSeed.CURRENT_ACCOUNT_ID,
            accountType = AccountType.PRIMARY,
            name = "Current",
            accountNumber = SarahMitchellSeed.CURRENT_ACCOUNT_NUMBER,
            createdAt = now.minus(31, ChronoUnit.DAYS),
            userId = sarahUserId,
            accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
        )
        seedUserAccount(
            accountId = SarahMitchellSeed.SAVINGS_ACCOUNT_ID,
            accountType = AccountType.SAVINGS,
            name = "Savings",
            accountNumber = SarahMitchellSeed.SAVINGS_ACCOUNT_NUMBER,
            createdAt = now.minus(31, ChronoUnit.DAYS),
            userId = sarahUserId,
            accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
        )

        log.info("seed: CURRENT-account transactions + instruments via the registration path")
        val result = registrationSeederService.seedFor(SarahMitchellSeed.EMAIL, SarahMitchellSeed.SEED)
        if (result !is SeedResult.Seeded) {
            log.warn(
                "seed: registration seeding did not produce a fresh dataset (result={}); " +
                    "skipping savings opening + completion marker. Run `clean` first.",
                result,
            )
            return
        }
        log.info(
            "seed: registration data — {} transactions, opening balance {} minor",
            result.transactionCount,
            result.openingBalanceMinor,
        )

        log.info("seed: flagship CREDIT_CARD estate — account + credit card + settled history + repayment")
        val creditCard = seedCreditEstate(now)

        log.info("seed: pending card authorisations on the CURRENT account (real holds)")
        seedPendingAuthorisations(now)

        log.info("seed: SAVINGS opening balance posting (D-30, seeder-only extra)")
        postSavingsOpening(now)

        log.info("seed: MDR demo worklist — {} model decisions via the service layer", MdrDemoSeed.DECISIONS.size)
        seedMdrDemoState()

        log.info(
            "seed: banking-day demo — {} new customers (+ deposits + cards), {} declined payments (screening story)",
            BankingDaySeed.NEW_CUSTOMERS.size,
            BankingDaySeed.DECLINED_PAYMENTS.size,
        )
        seedBankingDayDemo(now)

        log.info("seed: AML museum — {} cases with their monitored customers", AmlDemoSeed.CASES.size)
        seedAmlDemoState(now)

        log.info("seed: SEED_COMPLETED audit marker")
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_COMPLETED",
                afterState = mapOf(
                    "currentTransactionCount" to result.transactionCount,
                    "currentOpeningBalanceMinor" to result.openingBalanceMinor,
                    "savingsOpeningMinor" to SarahMitchellSeed.SAVINGS_OPENING_MINOR,
                    "creditOwedMinor" to SarahMitchellSeed.CREDIT_OWED_MINOR,
                ),
            )
        )

        log.info("seed: register the debit + credit card PANs with the stub scheme")
        registerCardPansWithStub(creditCard.pan)
    }

    // --- helpers -----------------------------------------------------------

    private fun seedUserAndHolder(now: Instant) {
        userRepository.save(
            UserEntity(
                id = sarahUserId,
                email = SarahMitchellSeed.EMAIL,
                firstName = SarahMitchellSeed.FIRST_NAME,
                lastName = SarahMitchellSeed.LAST_NAME,
                dateOfBirth = SarahMitchellSeed.DATE_OF_BIRTH,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = now.minus(31, ChronoUnit.DAYS),
                createdBy = "malai-seeder",
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_USER_CREATED",
                resourceType = "User",
                resourceId = sarahUserId.toString(),
            )
        )

        accountHolderRepository.save(
            AccountHolderEntity(
                accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                userId = sarahUserId,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = now.minus(31, ChronoUnit.DAYS),
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_HOLDER_CREATED",
                resourceType = "AccountHolder",
                resourceId = SarahMitchellSeed.ACCOUNT_HOLDER_ID.toString(),
            )
        )

        // Mirrors the real RegistrationOrchestrator path: every INDIVIDUAL holder
        // must have a matching `individuals` row, otherwise
        // AccountHolderService.resolveAccountHolderName throws 8990 on the
        // post-login `GET /account-holder/all` call.
        individualRepository.save(
            IndividualEntity(
                individualId = SarahMitchellSeed.INDIVIDUAL_ID,
                accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                title = null,
                firstName = SarahMitchellSeed.FIRST_NAME,
                lastName = SarahMitchellSeed.LAST_NAME,
                dateOfBirth = SarahMitchellSeed.DATE_OF_BIRTH,
                email = SarahMitchellSeed.EMAIL,
                phone = SarahMitchellSeed.PHONE,
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_INDIVIDUAL_CREATED",
                resourceType = "Individual",
                resourceId = SarahMitchellSeed.INDIVIDUAL_ID.toString(),
            )
        )

        addressRepository.save(
            AddressEntity(
                addressId = SarahMitchellSeed.ADDRESS_ID,
                accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                line1 = "62 Old Street",
                line2 = "Old borough",
                line3 = null,
                postTown = "Old Town",
                postCode = "AB1 2CD",
                countryCode = "UK",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.now().minusYears(7),
                isCurrent = true,
            )
        )

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "ADDRESS_ONBOARDED",
                resourceType = "AccountHolder",
                resourceId = SarahMitchellSeed.ACCOUNT_HOLDER_ID.toString(),
                afterState = mapOf("addressId" to SarahMitchellSeed.ADDRESS_ID.toString(), "postCode" to "AB1 2CD"),
            )
        )
    }

    // Every append goes through the MDR service layer — the same path production appends take —
    // so the chain append, the audit-plane double-entry and entry-id idempotency all fire exactly
    // as they would for a real integration. The fixed entry ids make this naturally idempotent:
    // a re-seed replays each append, finds the existing Entry by id and appends nothing new, which
    // is what makes seeding an append-only table compatible with clean's posture (mdr_entries,
    // like audit_events, is never deleted). Dispositions carry the ops reviewer as the ruling
    // human, so the worklist's ruled rows resolve to a real admin identity.
    private fun seedMdrDemoState() {
        MdrDemoSeed.DECISIONS.forEach { seed ->
            mdrDecisionService.recordDecision(
                RecordModelDecisionRequest(
                    entryId = seed.entryId,
                    decisionId = seed.decisionId,
                    correlationType = seed.correlationType,
                    correlationId = seed.correlationId,
                    advisory = false,
                    modelId = MdrDemoSeed.MODEL_ID,
                    modelVersion = MdrDemoSeed.MODEL_VERSION,
                    promptTemplateId = seed.promptTemplateId,
                    promptTemplateVersion = seed.promptTemplateVersion,
                    promptTemplateHash = seed.promptTemplateHash,
                    renderedPrompt = seed.renderedPrompt,
                    retrievedContext = seed.retrievedContext,
                    outcome = seed.outcome,
                    rawOutput = seed.rawOutput,
                    verdict = seed.verdict,
                    errorDetail = seed.errorDetail,
                    latencyMs = seed.latencyMs,
                    costMicros = seed.costMicros,
                    costCurrency = Currency.GBP,
                )
            )
            seed.dispositions.forEach { disposition ->
                mdrDispositionService.recordDisposition(
                    seed.decisionId,
                    opsUserId,
                    RecordHumanDispositionRequest(
                        entryId = disposition.entryId,
                        disposition = disposition.disposition,
                        editedOutput = disposition.editedOutput,
                    ),
                )
            }
        }
    }

    // A plausible Banking day for the Exec dashboard (BankingDaySeed): a cohort of customers
    // onboarded today so New customers / Cards issued / Deposits read a small bank's morning, and
    // the screening story so Payments stopped reads a real figure that reconciles with the Ops
    // worklist. Seeded after the MDR worklist so the screening decisions extend the same chain.
    private fun seedBankingDayDemo(now: Instant) {
        BankingDaySeed.NEW_CUSTOMERS.forEach { customer -> seedNewCustomer(customer, now) }
        BankingDaySeed.DECLINED_PAYMENTS.forEach { declined -> seedDeclinedPayment(declined, now) }
    }

    // One demo-day customer: a minimal-but-real estate created at run time (so the holder and the
    // debit card fall inside the Banking day) plus one inbound settled deposit. Not a login
    // persona — no Cognito identity is created — only genuine rows the metrics count.
    private fun seedNewCustomer(customer: BankingDaySeed.NewCustomer, now: Instant) {
        userRepository.save(
            UserEntity(
                id = customer.userId,
                email = customer.email,
                firstName = customer.firstName,
                lastName = customer.lastName,
                dateOfBirth = customer.dateOfBirth,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = now,
                createdBy = "malai-seeder",
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_USER_CREATED",
                resourceType = "User",
                resourceId = customer.userId.toString(),
            )
        )

        accountHolderRepository.save(
            AccountHolderEntity(
                accountHolderId = customer.accountHolderId,
                userId = customer.userId,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = now,
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_HOLDER_CREATED",
                resourceType = "AccountHolder",
                resourceId = customer.accountHolderId.toString(),
            )
        )

        individualRepository.save(
            IndividualEntity(
                individualId = customer.individualId,
                accountHolderId = customer.accountHolderId,
                title = null,
                firstName = customer.firstName,
                lastName = customer.lastName,
                dateOfBirth = customer.dateOfBirth,
                email = customer.email,
                phone = customer.phone,
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_INDIVIDUAL_CREATED",
                resourceType = "Individual",
                resourceId = customer.individualId.toString(),
            )
        )

        addressRepository.save(
            AddressEntity(
                addressId = customer.addressId,
                accountHolderId = customer.accountHolderId,
                line1 = customer.addressLine1,
                line2 = null,
                line3 = null,
                postTown = customer.postTown,
                postCode = customer.postCode,
                countryCode = "UK",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.now().minusYears(2),
                isCurrent = true,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "ADDRESS_ONBOARDED",
                resourceType = "AccountHolder",
                resourceId = customer.accountHolderId.toString(),
                afterState = mapOf("addressId" to customer.addressId.toString(), "postCode" to customer.postCode),
            )
        )

        seedUserAccount(
            accountId = customer.currentAccountId,
            accountType = AccountType.PRIMARY,
            name = "Current",
            accountNumber = customer.accountNumber,
            createdAt = now,
            userId = customer.userId,
            accountHolderId = customer.accountHolderId,
        )

        cardSeedHelper.issueCard(
            accountId = customer.currentAccountId,
            accountHolderId = customer.accountHolderId,
            cardholderName = customer.fullName,
            variant = "DEBIT",
            issuedAt = now,
            random = Random(customer.cardPanSeed),
            idGenerator = { customer.cardId },
        )

        insertPayment(
            paymentId = customer.depositPaymentId,
            direction = "INBOUND",
            state = "SETTLED",
            debtorAccountId = null,
            debtorSortCode = customer.depositOriginatorSortCode,
            debtorAccountNo = customer.depositOriginatorAccountNo,
            debtorName = customer.depositOriginatorName,
            beneficiaryAccountId = customer.currentAccountId,
            beneficiarySortCode = com.malai.commons.banking.MalaiSortCode.CANONICAL,
            beneficiaryAccountNo = customer.accountNumber,
            beneficiaryName = customer.fullName,
            amountMinorUnits = customer.depositAmountMinor,
            description = customer.depositDescription,
            endToEndId = customer.depositEndToEndId,
            createdAt = now,
            createdByAccountHolderId = customer.accountHolderId,
            screeningDecisionId = null,
        )
        auditWriter.record(
            AuditEvent(
                actorId = customer.accountHolderId,
                actorType = ActorType.SYSTEM,
                action = "SEED_INBOUND_PAYMENT_SETTLED",
                resourceType = "Payment",
                resourceId = customer.depositPaymentId.toString(),
                afterState = mapOf(
                    "state" to "SETTLED",
                    "direction" to "INBOUND",
                    "beneficiaryAccountId" to customer.currentAccountId.toString(),
                    "amountMinorUnits" to customer.depositAmountMinor,
                    "currency" to Currency.GBP.name,
                ),
            )
        )
    }

    // One leg of the screening story: an outbound FPS payment from Sarah that Screening referred
    // and a human ruled adversely. The payment is written straight to its terminal DECLINED state
    // (hold released, no ledger entries — exactly what the resolver would leave, which cannot run
    // here because payments-service is off the seeder's classpath), carrying the Model Decision's
    // id in screening_decision_id. The genuine PAYMENT-correlated Model Decision and the ops
    // reviewer's ACCEPT disposition are appended through the same MDR service layer as the worklist.
    private fun seedDeclinedPayment(declined: BankingDaySeed.DeclinedPayment, now: Instant) {
        insertPayment(
            paymentId = declined.paymentId,
            direction = "OUTBOUND",
            state = "DECLINED",
            debtorAccountId = SarahMitchellSeed.CURRENT_ACCOUNT_ID,
            debtorSortCode = null,
            debtorAccountNo = null,
            debtorName = null,
            beneficiaryAccountId = null,
            beneficiarySortCode = declined.beneficiarySortCode,
            beneficiaryAccountNo = declined.beneficiaryAccountNo,
            beneficiaryName = declined.beneficiaryName,
            amountMinorUnits = declined.amountMinor,
            description = declined.description,
            endToEndId = declined.endToEndId,
            createdAt = now,
            createdByAccountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
            screeningDecisionId = declined.decisionId,
        )

        mdrDecisionService.recordDecision(
            RecordModelDecisionRequest(
                entryId = declined.decisionEntryId,
                decisionId = declined.decisionId,
                correlationType = "PAYMENT",
                correlationId = declined.paymentId.toString(),
                advisory = false,
                modelId = BankingDaySeed.MODEL_ID,
                modelVersion = BankingDaySeed.MODEL_VERSION,
                promptTemplateId = BankingDaySeed.PROMPT_TEMPLATE_ID,
                promptTemplateVersion = BankingDaySeed.PROMPT_TEMPLATE_VERSION,
                promptTemplateHash = BankingDaySeed.PROMPT_TEMPLATE_HASH,
                renderedPrompt = declined.renderedPrompt,
                retrievedContext = listOf(
                    RetrievedContextRef(
                        type = "ACCOUNT",
                        id = SarahMitchellSeed.CURRENT_ACCOUNT_ID.toString(),
                        contentHash = BankingDaySeed.ACCOUNT_CONTEXT_HASH,
                    ),
                    RetrievedContextRef(
                        type = "WATCHLIST",
                        id = BankingDaySeed.WATCHLIST_VERSION,
                        contentHash = BankingDaySeed.WATCHLIST_CONTEXT_HASH,
                    ),
                ),
                outcome = ModelDecisionOutcome.COMPLETED,
                rawOutput = verdictSerialiser.writeValueAsString(declined.verdict),
                verdict = declined.verdict,
                errorDetail = null,
                latencyMs = declined.latencyMs,
                costMicros = 0L,
                costCurrency = Currency.GBP,
            )
        )

        mdrDispositionService.recordDisposition(
            declined.decisionId,
            opsUserId,
            RecordHumanDispositionRequest(
                entryId = declined.dispositionEntryId,
                disposition = HumanDispositionKind.ACCEPT,
                editedOutput = null,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                actorType = ActorType.SYSTEM,
                action = "SEED_PAYMENT_DECLINED",
                resourceType = "Payment",
                resourceId = declined.paymentId.toString(),
                afterState = mapOf(
                    "state" to "DECLINED",
                    "amountMinorUnits" to declined.amountMinor,
                    "currency" to Currency.GBP.name,
                    "screeningDecisionId" to declined.decisionId.toString(),
                ),
            )
        )
    }

    // The AML museum ([AmlDemoSeed]): two Cases an evaluator finds already on the worklist, one
    // closed with its filed SAR narrative and one open with unruled alerts. Every chain-bearing
    // write goes through the real service — the alert and SAR Model Decisions and their Human
    // Dispositions through the MDR service layer, the case transitions through AuditWriter — so
    // both chains verify over the museum exactly as they do over a live-fired case. Only the
    // `aml_cases` row itself lands through JDBC: aml-service is off the seeder's classpath by
    // architecture rule (nothing outside com.malai.aml and the com.malai.api aggregator may depend
    // on it), the same reason the demo-day payments are written directly. That table carries no
    // hash chain, so nothing chain-verified is bypassed.
    private fun seedAmlDemoState(now: Instant) {
        AmlDemoSeed.CASES.forEach { monitoredCase -> seedMonitoredCase(monitoredCase, now) }
    }

    private fun seedMonitoredCase(monitoredCase: AmlDemoSeed.MonitoredCase, now: Instant) {
        val customer = monitoredCase.customer
        val userId = monitoredUserIds.getValue(customer.email)

        seedRegisteredCustomerEstate(
            userId = userId,
            email = customer.email,
            firstName = customer.firstName,
            lastName = customer.lastName,
            dateOfBirth = customer.dateOfBirth,
            phone = customer.phone,
            accountHolderId = customer.accountHolderId,
            individualId = customer.individualId,
            addressId = customer.addressId,
            currentAccountId = customer.currentAccountId,
            accountNumber = customer.accountNumber,
            addressLine1 = customer.addressLine1,
            postTown = customer.postTown,
            postCode = customer.postCode,
            registrationSeed = customer.seed,
            createdAt = now.minus(31, ChronoUnit.DAYS),
        )

        // The credit the pattern was placed out of, and — for the live-fire persona — the headroom
        // a presenter spends firing the pattern again on stage. A ledger posting, not a payments
        // row: monitoring reads settled payments, so funding must not itself look like a typology.
        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(customer.fundingDaysAgo, ChronoUnit.DAYS),
            currentSideAccountId = customer.currentAccountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = customer.fundingCounterpartyName,
            amountOnCurrentSide = customer.fundingMinorUnits,
            description = customer.fundingDescription,
            type = "INCOMING_TRANSFER",
            initiatedById = customer.accountHolderId,
        )

        monitoredCase.evidence.forEach { payment -> seedEvidencePayment(customer, payment, now) }
        monitoredCase.alerts.forEach { alert -> recordAlert(customer, alert, now) }

        insertAmlCase(monitoredCase, now)
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "AML_CASE_OPENED",
                resourceType = "AmlCase",
                resourceId = monitoredCase.caseId.toString(),
                afterState = mapOf(
                    "accountId" to customer.currentAccountId.toString(),
                    "status" to AmlDemoSeed.CASE_OPEN,
                    "alertDecisionIds" to listOf(monitoredCase.alertDecisionIds.first().toString()),
                ),
            )
        )

        monitoredCase.sar?.let { sar -> recordSarDraftAndClose(monitoredCase, sar, now) }
    }

    // Each evidence payment is a settled outbound row AND its ledger pair. The pair is what makes
    // the museum survive the demo's hardest question: the presenter logs in as the monitored
    // customer, and the same payments the case file cites are in her own feed, with nothing on any
    // customer surface naming the investigation.
    private fun seedEvidencePayment(
        customer: AmlDemoSeed.MonitoredCustomer,
        payment: AmlDemoSeed.EvidencePayment,
        now: Instant,
    ) {
        val createdAt = payment.createdAt(now)
        insertPayment(
            paymentId = payment.paymentId,
            rail = payment.rail,
            direction = AmlDemoSeed.DIRECTION_OUTBOUND,
            state = "SETTLED",
            debtorAccountId = customer.currentAccountId,
            debtorSortCode = null,
            debtorAccountNo = null,
            debtorName = null,
            beneficiaryAccountId = null,
            beneficiarySortCode = payment.beneficiarySortCode,
            beneficiaryAccountNo = payment.beneficiaryAccountNo,
            beneficiaryName = payment.beneficiaryName,
            beneficiaryIban = payment.beneficiaryIban,
            beneficiaryBic = payment.beneficiaryBic,
            amountMinorUnits = payment.amountMinorUnits,
            description = payment.description,
            endToEndId = payment.endToEndId,
            createdAt = createdAt,
            createdByAccountHolderId = customer.accountHolderId,
            screeningDecisionId = null,
        )
        ledgerSeedHelper.postBalancedPair(
            postedAt = createdAt,
            currentSideAccountId = customer.currentAccountId,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = payment.beneficiaryName,
            amountOnCurrentSide = -payment.amountMinorUnits,
            description = payment.description,
            type = "PAYMENT_SETTLEMENT",
            initiatedById = customer.accountHolderId,
        )
    }

    // An alert IS its Model Decision — there is no alert table. Decision and entry ids are derived
    // from (account, typology, sorted evidence payment ids) by the same formula the live sweep
    // uses, so a re-sweep over the same payments would dedup against this row rather than raise a
    // second alert, and a re-seed replays the same fixed entry id and appends nothing new.
    private fun recordAlert(
        customer: AmlDemoSeed.MonitoredCustomer,
        alert: AmlDemoSeed.Alert,
        now: Instant,
    ) {
        val accountId = customer.currentAccountId
        mdrDecisionService.recordDecision(
            RecordModelDecisionRequest(
                entryId = alert.entryId(accountId),
                decisionId = alert.decisionId(accountId),
                correlationType = AmlDemoSeed.ACCOUNT_CORRELATION_TYPE,
                correlationId = accountId.toString(),
                advisory = false,
                modelId = AmlDemoSeed.ALERT_MODEL_ID,
                modelVersion = AmlDemoSeed.ALERT_MODEL_VERSION,
                promptTemplateId = AmlDemoSeed.ALERT_PROMPT_TEMPLATE_ID,
                promptTemplateVersion = AmlDemoSeed.ALERT_PROMPT_TEMPLATE_VERSION,
                promptTemplateHash = AmlDemoSeed.ALERT_PROMPT_TEMPLATE_HASH,
                renderedPrompt = alert.renderedPrompt(accountId, now),
                retrievedContext = alert.retrievedContext(now),
                outcome = ModelDecisionOutcome.COMPLETED,
                rawOutput = verdictSerialiser.writeValueAsString(alert.verdict()),
                verdict = alert.verdict(),
                errorDetail = null,
                latencyMs = alert.latencyMs,
                costMicros = 0L,
                costCurrency = Currency.GBP,
            )
        )
        alert.rulingEntryId?.let { rulingEntryId ->
            mdrDispositionService.recordDisposition(
                alert.decisionId(accountId),
                opsUserId,
                RecordHumanDispositionRequest(
                    entryId = rulingEntryId,
                    disposition = HumanDispositionKind.ACCEPT,
                    editedOutput = null,
                ),
            )
        }
    }

    // The SAR draft is its own grounded Model Decision, cited back to the alert and the payments
    // that alert fired over, and the close is what the Gate lets through: an ACCEPT in force makes
    // the drafted narrative the filed one. The narrative on the case row is that same string, so
    // the row and the record agree.
    private fun recordSarDraftAndClose(
        monitoredCase: AmlDemoSeed.MonitoredCase,
        sar: AmlDemoSeed.SarDraft,
        now: Instant,
    ) {
        val accountId = monitoredCase.customer.currentAccountId
        val alert = monitoredCase.alerts.first()
        val statements = sar.statements(monitoredCase.caseId, accountId, now, alert, now)
        val verdict = AmlDemoSeed.recordedVerdict(statements)

        mdrDecisionService.recordDecision(
            RecordModelDecisionRequest(
                entryId = sar.entryId,
                decisionId = sar.decisionId,
                correlationType = AmlDemoSeed.CASE_CORRELATION_TYPE,
                correlationId = monitoredCase.caseId.toString(),
                advisory = false,
                modelId = AmlDemoSeed.SAR_MODEL_ID,
                modelVersion = AmlDemoSeed.SAR_MODEL_VERSION,
                promptTemplateId = AmlDemoSeed.SAR_PROMPT_TEMPLATE_ID,
                promptTemplateVersion = AmlDemoSeed.SAR_PROMPT_TEMPLATE_VERSION,
                promptTemplateHash = AmlDemoSeed.SAR_PROMPT_TEMPLATE_HASH,
                renderedPrompt = sar.renderedPrompt(monitoredCase.caseId, accountId, now),
                retrievedContext = sar.retrievedContext(alert, accountId, now),
                outcome = ModelDecisionOutcome.COMPLETED,
                rawOutput = verdictSerialiser.writeValueAsString(verdict),
                verdict = verdict,
                errorDetail = null,
                latencyMs = sar.latencyMs,
                costMicros = 0L,
                costCurrency = Currency.GBP,
            )
        )
        mdrDispositionService.recordDisposition(
            sar.decisionId,
            opsUserId,
            RecordHumanDispositionRequest(
                entryId = sar.rulingEntryId,
                disposition = HumanDispositionKind.ACCEPT,
                editedOutput = null,
            ),
        )
        auditWriter.record(
            AuditEvent(
                actorId = opsUserId,
                actorType = ActorType.ADMIN,
                action = "AML_CASE_CLOSED_REPORTED",
                resourceType = "AmlCase",
                resourceId = monitoredCase.caseId.toString(),
                beforeState = mapOf("status" to "INVESTIGATING"),
                afterState = mapOf(
                    "status" to AmlDemoSeed.CASE_CLOSED_REPORTED,
                    "sarDecisionId" to sar.decisionId.toString(),
                    "ruling" to HumanDispositionKind.ACCEPT.name,
                    "rulingEntryId" to sar.rulingEntryId.toString(),
                ),
            )
        )
    }

    // Written in its terminal shape rather than opened and then transitioned: the partial unique
    // index that enforces one open case per account would otherwise collide with a case a
    // presenter live-fired onto the same account before re-seeding. The audit chain still carries
    // both transitions in production order.
    private fun insertAmlCase(monitoredCase: AmlDemoSeed.MonitoredCase, now: Instant) {
        val reported = monitoredCase.status == AmlDemoSeed.CASE_CLOSED_REPORTED
        val narrative = monitoredCase.sar
            ?.let { AmlDemoSeed.narrativeOf(it.statements(
                monitoredCase.caseId,
                monitoredCase.customer.currentAccountId,
                now,
                monitoredCase.alerts.first(),
                now,
            )) }
            ?.takeIf { reported }
        jdbcTemplate.update(
            """
            INSERT INTO aml_cases (
                case_id, account_id, status, alert_decision_ids,
                sar_decision_id, sar_narrative, opened_at, updated_at, closed_at
            ) VALUES (
                ?::uuid, ?::uuid, ?, ?::uuid[],
                ?::uuid, ?, ?, ?, ?
            )
            ON CONFLICT (case_id) DO NOTHING
            """.trimIndent(),
            monitoredCase.caseId.toString(),
            monitoredCase.customer.currentAccountId.toString(),
            monitoredCase.status,
            monitoredCase.alertDecisionIds.joinToString(",", "{", "}"),
            monitoredCase.sar?.decisionId?.toString(),
            narrative,
            Timestamp.from(now),
            Timestamp.from(now),
            if (reported) Timestamp.from(now) else null,
        )
    }

    // Direct write of a payments row. The seeder cannot reach payments-service (it is off the
    // classpath and would drag in the screening resolver + rail producers), so demo-day payments
    // land through JDBC. ON CONFLICT keeps a re-seed without a prior clean idempotent; the uuid
    // casts let a single statement carry the different nullable account-id columns of an inbound
    // deposit and an outbound decline.
    private fun insertPayment(
        paymentId: UUID,
        direction: String,
        state: String,
        debtorAccountId: UUID?,
        debtorSortCode: String?,
        debtorAccountNo: String?,
        debtorName: String?,
        beneficiaryAccountId: UUID?,
        beneficiarySortCode: String?,
        beneficiaryAccountNo: String?,
        beneficiaryName: String?,
        amountMinorUnits: Long,
        description: String,
        endToEndId: UUID,
        createdAt: Instant,
        createdByAccountHolderId: UUID,
        screeningDecisionId: UUID?,
        rail: String = "FPS",
        beneficiaryIban: String? = null,
        beneficiaryBic: String? = null,
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO payments (
                payment_id, rail, direction, state,
                debtor_account_id, debtor_sort_code, debtor_account_no, debtor_name,
                beneficiary_account_id, beneficiary_sort_code, beneficiary_account_no, beneficiary_name,
                beneficiary_iban, beneficiary_bic,
                amount_minor_units, currency, description, end_to_end_id, cop_verdict_override,
                created_at, created_by_account_holder_id, screening_decision_id
            ) VALUES (
                ?::uuid, ?, ?, ?,
                ?::uuid, ?, ?, ?,
                ?::uuid, ?, ?, ?,
                ?, ?,
                ?, 'GBP', ?, ?::uuid, FALSE,
                ?, ?::uuid, ?::uuid
            )
            ON CONFLICT (payment_id) DO NOTHING
            """.trimIndent(),
            paymentId.toString(),
            rail,
            direction,
            state,
            debtorAccountId?.toString(),
            debtorSortCode,
            debtorAccountNo,
            debtorName,
            beneficiaryAccountId?.toString(),
            beneficiarySortCode,
            beneficiaryAccountNo,
            beneficiaryName,
            beneficiaryIban,
            beneficiaryBic,
            amountMinorUnits,
            description,
            endToEndId.toString(),
            Timestamp.from(createdAt),
            createdByAccountHolderId.toString(),
            screeningDecisionId?.toString(),
        )
    }

    // The ops reviewer gets a `users` row only: no account holder, individual, address or
    // accounts. Back-office staff are not customers, and the admin surface never resolves a
    // customer estate for them. userRepository.save is an upsert by id, so a re-seed against an
    // already-seeded environment is a no-op for this row.
    private fun seedOpsReviewer(now: Instant) {
        userRepository.save(
            UserEntity(
                id = opsUserId,
                email = OpsReviewerSeed.EMAIL,
                firstName = OpsReviewerSeed.FIRST_NAME,
                lastName = OpsReviewerSeed.LAST_NAME,
                dateOfBirth = OpsReviewerSeed.DATE_OF_BIRTH,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = now.minus(31, ChronoUnit.DAYS),
                createdBy = "malai-seeder",
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_OPS_REVIEWER_CREATED",
                resourceType = "User",
                resourceId = opsUserId.toString(),
            )
        )
    }

    // The narrow-role gating persona (ADR-0010): a `users` row only — no account holder,
    // individual, address or accounts. A manager is neither a customer nor an admin, so the
    // admin surface never resolves a customer estate for them and the holder-scoped endpoints
    // degrade to 403/4999. userRepository.save is an upsert by id, so a re-seed against an
    // already-seeded environment is a no-op for this row.
    private fun seedManagerOnlyUser(now: Instant) {
        userRepository.save(
            UserEntity(
                id = managerUserId,
                email = ManagerOnlySeed.EMAIL,
                firstName = ManagerOnlySeed.FIRST_NAME,
                lastName = ManagerOnlySeed.LAST_NAME,
                dateOfBirth = ManagerOnlySeed.DATE_OF_BIRTH,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = now.minus(31, ChronoUnit.DAYS),
                createdBy = "malai-seeder",
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_MANAGER_CREATED",
                resourceType = "User",
                resourceId = managerUserId.toString(),
            )
        )
    }

    // The surface-gating persona: a full (if minimal) customer estate — user, holder,
    // individual, address, one CURRENT account — with transactions, instruments and a
    // debit card delegated to the same deterministic registration path Sarah uses.
    // Seeded BEFORE Sarah's registration data so a partially-seeded environment
    // (where Sarah's Noop short-circuits the rest of the seed) still gains this user.
    private fun seedCustomerOnlyUser(now: Instant) {
        seedRegisteredCustomerEstate(
            userId = customerOnlyUserId,
            email = CustomerOnlySeed.EMAIL,
            firstName = CustomerOnlySeed.FIRST_NAME,
            lastName = CustomerOnlySeed.LAST_NAME,
            dateOfBirth = CustomerOnlySeed.DATE_OF_BIRTH,
            phone = CustomerOnlySeed.PHONE,
            accountHolderId = CustomerOnlySeed.ACCOUNT_HOLDER_ID,
            individualId = CustomerOnlySeed.INDIVIDUAL_ID,
            addressId = CustomerOnlySeed.ADDRESS_ID,
            currentAccountId = CustomerOnlySeed.CURRENT_ACCOUNT_ID,
            accountNumber = CustomerOnlySeed.CURRENT_ACCOUNT_NUMBER,
            addressLine1 = "17 Cedar Lane",
            postTown = "New Town",
            postCode = "CD3 4EF",
            registrationSeed = CustomerOnlySeed.SEED,
            createdAt = now.minus(31, ChronoUnit.DAYS),
        )
    }

    // One customer, onboarded the way a self-registered one is: identity rows keyed on the pinned
    // (or Cognito-resolved) ids, then transactions, instruments and a debit card delegated to the
    // same deterministic registration path Sarah uses.
    private fun seedRegisteredCustomerEstate(
        userId: UUID,
        email: String,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate,
        phone: String,
        accountHolderId: UUID,
        individualId: UUID,
        addressId: UUID,
        currentAccountId: UUID,
        accountNumber: String,
        addressLine1: String,
        postTown: String,
        postCode: String,
        registrationSeed: Long,
        createdAt: Instant,
    ) {
        userRepository.save(
            UserEntity(
                id = userId,
                email = email,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = createdAt,
                createdBy = "malai-seeder",
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_USER_CREATED",
                resourceType = "User",
                resourceId = userId.toString(),
            )
        )

        accountHolderRepository.save(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = userId,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = createdAt,
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_HOLDER_CREATED",
                resourceType = "AccountHolder",
                resourceId = accountHolderId.toString(),
            )
        )

        individualRepository.save(
            IndividualEntity(
                individualId = individualId,
                accountHolderId = accountHolderId,
                title = null,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                email = email,
                phone = phone,
                status = OnboardingStatus.ACTIVE,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_INDIVIDUAL_CREATED",
                resourceType = "Individual",
                resourceId = individualId.toString(),
            )
        )

        addressRepository.save(
            AddressEntity(
                addressId = addressId,
                accountHolderId = accountHolderId,
                line1 = addressLine1,
                line2 = null,
                line3 = null,
                postTown = postTown,
                postCode = postCode,
                countryCode = "UK",
                udprn = null,
                umprn = null,
                fromDate = LocalDate.now().minusYears(4),
                isCurrent = true,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "ADDRESS_ONBOARDED",
                resourceType = "AccountHolder",
                resourceId = accountHolderId.toString(),
                afterState = mapOf("addressId" to addressId.toString(), "postCode" to postCode),
            )
        )

        seedUserAccount(
            accountId = currentAccountId,
            accountType = AccountType.PRIMARY,
            name = "Current",
            accountNumber = accountNumber,
            createdAt = createdAt,
            userId = userId,
            accountHolderId = accountHolderId,
        )

        val result = registrationSeederService.seedFor(email, registrationSeed)
        if (result !is SeedResult.Seeded) {
            log.warn("seed: registration seeding for {} did not produce a fresh dataset (result={})", email, result)
        }
    }

    private fun seedUserAccount(
        accountId: UUID,
        accountType: AccountType,
        name: String,
        accountNumber: String,
        createdAt: Instant,
        userId: UUID,
        accountHolderId: UUID,
    ) {
        accountRepository.save(
            AccountEntity(
                accountId = accountId,
                accountType = accountType,
                defaultCategory = uuidGenerator.generate(),
                currency = Currency.GBP,
                createdAt = createdAt,
                name = name,
                userId = userId,
                accountHolderId = accountHolderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
        // Register the seeded customer account in the ledger registry (the seeder writes
        // directly, bypassing account-service.createAccount), so book transfers between
        // Sarah's accounts post through the ledger funds guard.
        jdbcTemplate.update(
            "INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy) " +
                "VALUES (?, 'GBP', 'CUSTOMER', 'NO_OVERDRAFT') ON CONFLICT (account_id) DO NOTHING",
            accountId,
        )
        // SORT_CODE identifier for the canonical Malai sort code (Plan 02-09).
        accountIdentifierRepository.save(
            AccountIdentifierEntity(
                identifierId = uuidGenerator.generate(),
                accountId = accountId,
                identifierType = IdentifierType.SORT_CODE,
                bankIdentifier = com.malai.commons.banking.MalaiSortCode.CANONICAL,
                accountIdentifier = accountNumber,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_CREATED",
                resourceType = "Account",
                resourceId = accountId.toString(),
                afterState = mapOf("type" to accountType.name, "name" to name),
            )
        )
    }

    // The flagship CREDIT_CARD estate (cards issue 12). Mirrors AccountsService.applyForCreditCard
    // but writes directly (like the rest of the seeder) so the account, ledger registration and
    // card all backdate: a CREDIT_CARD account registered FLOORED at −£2500 (the credit limit),
    // a CREDIT card, and months of settled merchant purchases less a partial repayment leaving the
    // ledger balance at −£350.00 (owed £350.00 → the StatementCalculator's 2.5% minimum branch, not
    // the £5 floor). Returns the seeded credit card so its PAN can be registered with the stub.
    private fun seedCreditEstate(now: Instant): CardSeedHelper.SeededCard {
        val createdAt = now.minus(90, ChronoUnit.DAYS)

        accountRepository.save(
            AccountEntity(
                accountId = SarahMitchellSeed.CREDIT_ACCOUNT_ID,
                accountType = AccountType.CREDIT_CARD,
                defaultCategory = uuidGenerator.generate(),
                currency = Currency.GBP,
                createdAt = createdAt,
                name = CREDIT_CARD_ACCOUNT_NAME,
                userId = sarahUserId,
                accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                status = OnboardingStatus.ACTIVE,
            )
        )
        // FLOORED at the negative credit limit — settled purchases push the balance negative (owed)
        // down to this floor, exactly like a registered credit-card ledger account.
        jdbcTemplate.update(
            "INSERT INTO ledger_accounts (account_id, currency, kind, guard_policy, floor_minor_units) " +
                "VALUES (?, 'GBP', 'CUSTOMER', 'FLOORED', ?) ON CONFLICT (account_id) DO NOTHING",
            SarahMitchellSeed.CREDIT_ACCOUNT_ID,
            CREDIT_CARD_FLOOR_MINOR,
        )
        accountIdentifierRepository.save(
            AccountIdentifierEntity(
                identifierId = uuidGenerator.generate(),
                accountId = SarahMitchellSeed.CREDIT_ACCOUNT_ID,
                identifierType = IdentifierType.SORT_CODE,
                bankIdentifier = com.malai.commons.banking.MalaiSortCode.CANONICAL,
                accountIdentifier = SarahMitchellSeed.CREDIT_ACCOUNT_NUMBER,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "SEED_ACCOUNT_CREATED",
                resourceType = "Account",
                resourceId = SarahMitchellSeed.CREDIT_ACCOUNT_ID.toString(),
                afterState = mapOf("type" to AccountType.CREDIT_CARD.name, "name" to CREDIT_CARD_ACCOUNT_NAME),
            )
        )

        val creditCard = cardSeedHelper.issueCard(
            accountId = SarahMitchellSeed.CREDIT_ACCOUNT_ID,
            accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
            cardholderName = SarahMitchellSeed.FULL_NAME,
            variant = "CREDIT",
            issuedAt = createdAt,
            random = Random(SarahMitchellSeed.CREDIT_CARD_PAN_SEED),
            idGenerator = { SarahMitchellSeed.CREDIT_CARD_ID },
        )

        CREDIT_PURCHASES.forEach { purchase ->
            ledgerSeedHelper.postBalancedPair(
                postedAt = now.minus(purchase.daysAgo, ChronoUnit.DAYS),
                currentSideAccountId = SarahMitchellSeed.CREDIT_ACCOUNT_ID,
                counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
                counterpartyName = purchase.merchantName,
                amountOnCurrentSide = -purchase.amountMinor,
                description = purchase.merchantName,
                type = "CARD_PAYMENT",
                initiatedById = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
            )
        }
        // The partial repayment credit — the "part-paid" in "part-paid current statement".
        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(15, ChronoUnit.DAYS),
            currentSideAccountId = SarahMitchellSeed.CREDIT_ACCOUNT_ID,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = "Payment received",
            amountOnCurrentSide = SarahMitchellSeed.CREDIT_REPAYMENT_MINOR,
            description = UkMerchantsSeed.CC_PAYMENT,
            type = "INCOMING_TRANSFER",
            initiatedById = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
        )

        return creditCard
    }

    // One or two live, unsettled card authorisations on the CURRENT account so the feed shows
    // PENDING rows on first open. Each is a real card_authorisations row (AUTHORISED) against the
    // real debit card, paired with a PLACED ledger hold so the CURRENT account's available balance
    // genuinely drops (available = ledger − Σ PLACED holds) — not a display fake. hold_id ==
    // authorisation_id, matching the runtime authorise path.
    private fun seedPendingAuthorisations(now: Instant) {
        val debitCardId = jdbcTemplate.query(
            "SELECT card_id FROM cards WHERE account_id = ? AND variant = 'DEBIT'",
            { rs, _ -> rs.getObject("card_id", UUID::class.java) },
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        ).firstOrNull()
        if (debitCardId == null) {
            log.warn("seed: no debit card on the CURRENT account; skipping pending authorisations")
            return
        }

        PENDING_AUTHORISATIONS.forEach { auth ->
            val placedAt = now.minus(auth.minutesAgo, ChronoUnit.MINUTES)
            ledgerSeedHelper.placeHold(
                holdId = auth.authorisationId,
                accountId = SarahMitchellSeed.CURRENT_ACCOUNT_ID,
                amountMinorUnits = auth.amountMinor,
                placedAt = placedAt,
            )
            cardSeedHelper.placeAuthorisation(
                authorisationId = auth.authorisationId,
                cardId = debitCardId,
                accountId = SarahMitchellSeed.CURRENT_ACCOUNT_ID,
                accountHolderId = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
                schemeReference = auth.schemeReference,
                merchantName = auth.merchantName,
                mcc = auth.mcc,
                amountMinorUnits = auth.amountMinor,
                currency = Currency.GBP.name,
                holdId = auth.authorisationId,
                createdAt = placedAt,
            )
        }
    }

    // Registers the debit + credit PANs with the stub's PAN registry so the ambient card-spend
    // scheduler originates purchases against Sarah's cards (live-registered users stay unregistered
    // and drive spend via the sandbox). Best-effort: a stub outage must not fail the seed — the
    // estate is already committed and ambient spend is a demo nicety, not a correctness invariant.
    private fun registerCardPansWithStub(creditPan: String) {
        val debitPan = jdbcTemplate.query(
            "SELECT pan FROM cards WHERE account_id = ? AND variant = 'DEBIT'",
            { rs, _ -> rs.getString("pan") },
            SarahMitchellSeed.CURRENT_ACCOUNT_ID,
        ).firstOrNull()
        val pans = listOfNotNull(debitPan, creditPan)
        try {
            pans.forEach { malaiStubClient.registerCardPan(it) }
            log.info("seed: registered {} card PAN(s) with the stub scheme", pans.size)
        } catch (ex: Exception) {
            log.warn(
                "seed: PAN registration with the stub scheme failed (fail-open); " +
                    "ambient card spend will not target the seeded cards",
                ex,
            )
        }
    }

    // Sarah's SAVINGS account is a seeder-only extra (a real registration onboards a
    // single CURRENT account). One D-30 opening posting lands it at £8400.00; the
    // counterparty leg is the bank's SCHEME_SETTLEMENT system account (V16), with the
    // ACME narrative preserved on the ledger entry's merchantName for UI display.
    private fun postSavingsOpening(now: Instant) {
        ledgerSeedHelper.postBalancedPair(
            postedAt = now.minus(30, ChronoUnit.DAYS),
            currentSideAccountId = SarahMitchellSeed.SAVINGS_ACCOUNT_ID,
            counterpartyAccountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
            counterpartyName = ClearingAccountsSeed.ACME.name,
            amountOnCurrentSide = SarahMitchellSeed.SAVINGS_OPENING_MINOR,
            description = UkMerchantsSeed.SAVINGS_OPEN,
            type = "OPENING_BALANCE",
            initiatedById = SarahMitchellSeed.ACCOUNT_HOLDER_ID,
        )
    }

    private data class CreditPurchase(val merchantName: String, val amountMinor: Long, val daysAgo: Long)

    private data class PendingAuth(
        val authorisationId: UUID,
        val schemeReference: UUID,
        val merchantName: String,
        val mcc: String,
        val amountMinor: Long,
        val minutesAgo: Long,
    )

    private companion object {
        const val CREDIT_CARD_ACCOUNT_NAME = "Credit Card"
        const val CREDIT_CARD_FLOOR_MINOR = -250_000L

        // Settled merchant purchases on the credit account, using the stub's fictional merchant
        // directory so the seeded history and the ambient card spend read as one estate. Total
        // £440.00; less the £90.00 repayment they net to owed £350.00 (> £200, so the 2.5% minimum
        // branch fires). Spread across the last three cycles for "several months" of history.
        val CREDIT_PURCHASES: List<CreditPurchase> = listOf(
            CreditPurchase("Atlas Electronics", 22_000L, 70),
            CreditPurchase("Nimbus Groceries", 8_600L, 55),
            CreditPurchase("Harbour Fuel", 6_500L, 40),
            CreditPurchase("Fictional Coffee Co", 1_450L, 20),
            CreditPurchase("Orbit Transit", 4_451L, 10),
            CreditPurchase("Lumen Streaming", 999L, 5),
        )

        // Two live pending authorisations on the CURRENT account (£52.00 + £18.75 = £70.75 held).
        val PENDING_AUTHORISATIONS: List<PendingAuth> = listOf(
            PendingAuth(
                SarahMitchellSeed.PENDING_AUTH_1_ID,
                SarahMitchellSeed.PENDING_AUTH_1_SCHEME_REF,
                "Harbour Fuel",
                "5541",
                5_200L,
                minutesAgo = 24 * 60,
            ),
            PendingAuth(
                SarahMitchellSeed.PENDING_AUTH_2_ID,
                SarahMitchellSeed.PENDING_AUTH_2_SCHEME_REF,
                "Nimbus Groceries",
                "5411",
                1_875L,
                minutesAgo = 6 * 60,
            ),
        )
    }
}
