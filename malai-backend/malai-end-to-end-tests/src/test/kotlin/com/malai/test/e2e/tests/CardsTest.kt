package com.malai.test.e2e.tests

import tools.jackson.module.kotlin.readValue
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardGateway
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardScheme
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CardService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.LedgerService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.SandboxService
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.WithFunding.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.fixtures.readAccountSortCodeAndNumber
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.UUID

class CardsTest : MalaiE2ETest() {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun debitPurchaseAuthorisesPresentsThenAFrozenCardVisiblyDeclines() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingBalanceIsSeededOnAccount(accountId = accountId, amountMinorUnits = 50_000L))

        whenever(cardServiceReceivesGetCards(userId = someUserId(), accountHolderId = accountHolderId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val cardId = uuidFromLastResponseList("cards", 0, "cardId")

        whenever(sandboxServiceReceivesGetCardMerchants(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val (merchantId, merchantName) = firstMerchantInLastResponse()

        whenever(sandboxServiceReceivesCardPurchase(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            cardId = cardId,
            merchantId = merchantId,
            amountMinorUnits = 4_200L,
        ))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To something
                "outcome" To "APPROVED"
                noExtraProps()
            }
        }
        val purchaseSchemeReference = uuidFromLastResponse("schemeReference")

        and(theCardAuthorisationReachesStatusInTheDatabase(purchaseSchemeReference, "AUTHORISED"))

        whenever(accountServiceReceivesGetBalance(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 50_000
                "availableBalanceMinorUnits" To 45_800
                "heldAmountMinorUnits" To 4_200
                noExtraProps()
            }
        }

        whenever(accountServiceReceivesGetTransactions(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val pendingRow = transactionsInLastResponse().single { it["status"] == "PENDING" }
        pendingRow["merchantName"] shouldBe merchantName
        pendingRow["cardId"] shouldBe cardId.toString()
        (pendingRow["amountMinorUnits"] as Number).toLong() shouldBe -4_200L

        and(theFollowingHttpRequestWasMade(SandboxService, CardScheme) {
            method == POST && url.endsWith("/scenarios/cards/purchase")
        }) { method shouldBe POST }
        and(theFollowingHttpRequestWasMade(CardScheme, CardGateway) {
            method == POST && url.endsWith("/cards/callbacks/authorise")
        }) { method shouldBe POST }
        and(theFollowingHttpRequestWasMade(CardGateway, CardService) {
            method == POST && url.endsWith("/api/v1/internal/cards/authorisations")
        }) { method shouldBe POST }
        and(theFollowingHttpRequestWasMade(CardService, LedgerService) {
            method == POST
        }) { method shouldBe POST }

        whenever(sandboxServiceReceivesCardPresentNow(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }

        and(theCardAuthorisationReachesStatusInTheDatabase(purchaseSchemeReference, "SETTLED"))

        whenever(accountServiceReceivesGetBalance(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 45_800
                "availableBalanceMinorUnits" To 45_800
                "heldAmountMinorUnits" To 0
                noExtraProps()
            }
        }

        whenever(accountServiceReceivesGetTransactions(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val settledFeed = transactionsInLastResponse()
        settledFeed.none { it["status"] == "PENDING" } shouldBe true
        val settledRow = settledFeed.single { (it["amountMinorUnits"] as Number).toLong() == -4_200L }
        settledRow["merchantName"] shouldBe merchantName
        settledRow["status"] shouldBe null

        and(theLedgerEntriesForAccountInTheDatabase(accountId)) {
            this shouldHaveSize 2
            this[0].amountMinorUnits shouldBe 50_000L
            this[1].amountMinorUnits shouldBe -4_200L
        }
        and(theLedgerEntriesForAccountInTheDatabase(SCHEME_SETTLEMENT_ACCOUNT_ID)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 4_200L
        }

        and(theFollowingHttpRequestWasMade(SandboxService, CardScheme) {
            method == POST && url.endsWith("/scenarios/cards/present-now")
        }) { method shouldBe POST }
        and(theFollowingHttpRequestWasMade(CardScheme, CardGateway) {
            method == POST && url.endsWith("/cards/callbacks/present")
        }) { method shouldBe POST }

        whenever(cardServiceReceivesFreezeCard(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            cardId = cardId,
            idempotencyKey = "e2e-freeze-1",
        ))
        then(theLastResponseHas()) { statusCode shouldBe OK }

        whenever(sandboxServiceReceivesCardPurchase(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            cardId = cardId,
            merchantId = merchantId,
            amountMinorUnits = 2_500L,
        ))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To something
                "outcome" To "DECLINED"
                "declineReasonCode" To something
                noExtraProps()
            }
        }
        val declinedSchemeReference = uuidFromLastResponse("schemeReference")

        and(theCardAuthorisationReachesStatusInTheDatabase(declinedSchemeReference, "DECLINED"))

        whenever(accountServiceReceivesGetTransactions(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val declinedRow = transactionsInLastResponse().single { it["status"] == "DECLINED" }
        declinedRow["declineReason"] shouldBe "CARD_FROZEN"
        declinedRow["merchantName"] shouldBe merchantName
        declinedRow["cardId"] shouldBe cardId.toString()

        whenever(accountServiceReceivesGetBalance(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 45_800
                "availableBalanceMinorUnits" To 45_800
                "heldAmountMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    @Test
    fun creditCardAppliedSpentStatementDerivedThenRepaidByBookTransfer() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val primaryAccountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingBalanceIsSeededOnAccount(accountId = primaryAccountId, amountMinorUnits = 20_000L))

        val expectedDueDate = YearMonth.now(ZoneOffset.UTC).plusMonths(1).atDay(21).toString()

        whenever(accountServiceReceivesCreditCardApplication(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-credit-app-1",
        ))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val (creditAccountId, creditCardId) = creditAccountAndCardFromLastResponse()

        and(theFollowingHttpRequestWasMade(AccountService, CardService) {
            method == POST && url.endsWith("/api/v1/internal/cards")
        }) { method shouldBe POST }

        whenever(accountServiceReceivesGetCreditSummary(someUserId(), accountHolderId, creditAccountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250_000
                "owedMinorUnits" To 0
                "availableCreditMinorUnits" To 250_000
                "statementBalanceMinorUnits" To 0
                "minimumPaymentMinorUnits" To 0
                "dueDate" To expectedDueDate
                noExtraProps()
            }
        }

        whenever(sandboxServiceReceivesGetCardMerchants(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val (merchantId, _) = firstMerchantInLastResponse()

        whenever(sandboxServiceReceivesCardPurchase(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            cardId = creditCardId,
            merchantId = merchantId,
            amountMinorUnits = 30_000L,
        ))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To something
                "outcome" To "APPROVED"
                noExtraProps()
            }
        }
        val purchaseSchemeReference = uuidFromLastResponse("schemeReference")

        and(theCardAuthorisationReachesStatusInTheDatabase(purchaseSchemeReference, "AUTHORISED"))

        whenever(sandboxServiceReceivesCardPresentNow(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }

        and(theCardAuthorisationReachesStatusInTheDatabase(purchaseSchemeReference, "SETTLED"))

        whenever(accountServiceReceivesGetCreditSummary(someUserId(), accountHolderId, creditAccountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250_000
                "owedMinorUnits" To 30_000
                "availableCreditMinorUnits" To 220_000
                "statementBalanceMinorUnits" To 30_000
                "minimumPaymentMinorUnits" To 750
                "dueDate" To expectedDueDate
                noExtraProps()
            }
        }

        val (creditSortCode, creditAccountNumber) = readAccountSortCodeAndNumber(
            accountId = creditAccountId,
            userId = someUserId(),
            accountHolderId = accountHolderId,
        )

        whenever(accountServiceReceivesTransfer(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-pay-card-1",
            fromAccountId = primaryAccountId,
            toSortCode = creditSortCode,
            toAccountNumber = creditAccountNumber,
            amountMinorUnits = 10_000L,
            description = "Pay card",
        ))
        then(theLastResponseHas()) { statusCode shouldBe CREATED }

        whenever(accountServiceReceivesGetCreditSummary(someUserId(), accountHolderId, creditAccountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250_000
                "owedMinorUnits" To 20_000
                "availableCreditMinorUnits" To 230_000
                "statementBalanceMinorUnits" To 20_000
                "minimumPaymentMinorUnits" To 500
                "dueDate" To expectedDueDate
                noExtraProps()
            }
        }
    }

    @Test
    fun settledPurchaseRefundedAppearsAsAMerchantNamedCredit() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingBalanceIsSeededOnAccount(accountId = accountId, amountMinorUnits = 50_000L))

        whenever(cardServiceReceivesGetCards(userId = someUserId(), accountHolderId = accountHolderId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val cardId = uuidFromLastResponseList("cards", 0, "cardId")

        whenever(sandboxServiceReceivesGetCardMerchants(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val (merchantId, merchantName) = firstMerchantInLastResponse()

        whenever(sandboxServiceReceivesCardPurchase(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            cardId = cardId,
            merchantId = merchantId,
            amountMinorUnits = 4_200L,
        ))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To something
                "outcome" To "APPROVED"
                noExtraProps()
            }
        }
        val purchaseSchemeReference = uuidFromLastResponse("schemeReference")

        whenever(sandboxServiceReceivesCardPresentNow(userId = someUserId()))
        then(theLastResponseHas()) { statusCode shouldBe OK }

        and(theCardAuthorisationReachesStatusInTheDatabase(purchaseSchemeReference, "SETTLED"))

        val authorisationId = theSettledAuthorisationIdForSchemeReference(purchaseSchemeReference)

        whenever(sandboxServiceReceivesCardRefund(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            authorisationId = authorisationId,
        ))
        then(theLastResponseHas()) { statusCode shouldBe ACCEPTED }

        and(theCardRefundForAuthorisationReachesTheDatabase(authorisationId))

        and(theFollowingHttpRequestWasMade(SandboxService, CardScheme) {
            method == POST && url.endsWith("/scenarios/cards/refund")
        }) { method shouldBe POST }
        and(theFollowingHttpRequestWasMade(CardScheme, CardGateway) {
            method == POST && url.endsWith("/cards/callbacks/refund")
        }) { method shouldBe POST }

        whenever(accountServiceReceivesGetTransactions(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) { statusCode shouldBe OK }
        val refundRow = transactionsInLastResponse().single { (it["amountMinorUnits"] as Number).toLong() == 4_200L }
        refundRow["merchantName"] shouldBe merchantName
        refundRow["status"] shouldBe null

        and(theLedgerEntriesForAccountInTheDatabase(accountId)) {
            this.map { it.amountMinorUnits }.sorted() shouldBe listOf(-4_200L, 4_200L, 50_000L)
        }
        and(theLedgerEntriesForAccountInTheDatabase(SCHEME_SETTLEMENT_ACCOUNT_ID)) {
            this.map { it.amountMinorUnits }.sorted() shouldBe listOf(-4_200L, 4_200L)
        }

        whenever(accountServiceReceivesGetBalance(someUserId(), accountHolderId, accountId))
        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 50_000
                "availableBalanceMinorUnits" To 50_000
                "heldAmountMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    private fun firstMerchantInLastResponse(): Pair<String, String> {
        val body = lastResponse().body ?: throw AssertionError("Response body was null")
        @Suppress("UNCHECKED_CAST")
        val merchants = testingObjectMapper.readValue<Map<String, Any?>>(body)["merchants"] as List<Map<String, Any?>>
        val first = merchants.first()
        return (first["merchantId"] as String) to (first["name"] as String)
    }

    private fun transactionsInLastResponse(): List<Map<String, Any?>> {
        val body = lastResponse().body ?: throw AssertionError("Response body was null")
        @Suppress("UNCHECKED_CAST")
        return testingObjectMapper.readValue<Map<String, Any?>>(body)["transactions"] as List<Map<String, Any?>>
    }

    private fun creditAccountAndCardFromLastResponse(): Pair<UUID, UUID> {
        val body = lastResponse().body ?: throw AssertionError("Response body was null")
        val application = testingObjectMapper.readValue<Map<String, Any?>>(body)
        @Suppress("UNCHECKED_CAST")
        val account = application["account"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val card = application["card"] as Map<String, Any?>
        account["accountType"] shouldBe "CREDIT_CARD"
        card["variant"] shouldBe "CREDIT"
        card["status"] shouldBe "ACTIVE"
        return UUID.fromString(account["accountUid"] as String) to UUID.fromString(card["cardId"] as String)
    }
}
