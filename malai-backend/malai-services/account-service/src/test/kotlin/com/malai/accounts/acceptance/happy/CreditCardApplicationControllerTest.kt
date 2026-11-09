package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.card.builders.response.StatementFiguresResponseBuilder.Companion.statementFigures
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.commons.model.Currency.GBP
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.CallableAction
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

class CreditCardApplicationControllerTest : AccountsAcceptanceTest() {

    private fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    private val listHolder = UUID.fromString("00000000-0000-4000-8801-000000000001")
    private val listAccount = UUID.fromString("00000000-0000-4000-8801-000000000002")
    private val summaryHolder = UUID.fromString("00000000-0000-4000-8801-000000000003")
    private val freshAccount = UUID.fromString("00000000-0000-4000-8801-000000000004")
    private val holdsAccount = UUID.fromString("00000000-0000-4000-8801-000000000005")
    private val settledAccount = UUID.fromString("00000000-0000-4000-8801-000000000006")
    private val overpaidAccount = UUID.fromString("00000000-0000-4000-8801-000000000007")
    private val failOpenAccount = UUID.fromString("00000000-0000-4000-8801-000000000008")

    @Test
    fun applicationApprovesInstantlyCreatingAFlooredCreditCardAccountAndCreditCard() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(someAccountUid())))
        and(issueCardReturns(
            anIssueCardResponse()
                .withCardId(someCardUid())
                .withAccountId(someAccountUid())
                .withMaskedPan("************0000")
                .withVariant("CREDIT")
                .withStatus("ACTIVE")
                .withExpiry("10/29")
        ))
        and(theNextGeneratedUuidWillBe(someAccountUid(), someDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("50000001"))
        and(nowIs("2026-07-12T10:30:00Z"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/${someAccountHolderUid()}")
                .header("Idempotency-Key", "cc-application-happy")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "account" To json {
                    "accountUid" To someAccountUid().toString()
                    "accountType" To "CREDIT_CARD"
                    "defaultCategory" To someDefaultCategory().toString()
                    "currency" To "GBP"
                    "createdAt" To "2026-07-12T10:30:00Z"
                    "name" To "Credit Card"
                    noExtraProps()
                }
                "card" To json {
                    "cardId" To someCardUid().toString()
                    "accountId" To someAccountUid().toString()
                    "maskedPan" To "************0000"
                    "variant" To "CREDIT"
                    "status" To "ACTIVE"
                    "expiry" To "10/29"
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(theAccountInTheDatabase(someAccountUid())) {
            this shouldBeEqualUsingFields AccountEntity(
                accountId = someAccountUid(),
                accountType = AccountType.CREDIT_CARD,
                defaultCategory = someDefaultCategory(),
                currency = GBP,
                createdAt = Instant.parse("2026-07-12T10:30:00Z"),
                name = "Credit Card",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = ACTIVE,
            )
        }

        and(theIdentifiersInTheDatabase(someAccountUid())) {
            this shouldHaveSize 2

            this[0].identifierType shouldBe SORT_CODE
            this[0].accountId shouldBe someAccountUid()
            this[0].bankIdentifier shouldBe "65-82-37"
            this[0].accountIdentifier shouldBe "50000001"

            this[1].identifierType shouldBe IBAN_BIC
            this[1].accountId shouldBe someAccountUid()
            this[1].bankIdentifier shouldBe "MALAGB22XXX"
            this[1].accountIdentifier shouldStartWith "GB"
            this[1].accountIdentifier shouldEndWith "50000001"
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "CREDIT_CARD_APPLICATION_APPROVED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Account"
            this[0]["resource_id"] shouldBe someAccountUid().toString()
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts"
        }) {
            method shouldBe POST
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "FLOORED"
                "floorMinorUnits" To -250000
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards"
        }) {
            method shouldBe POST
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "cardholderName" To "Credit Card"
                "variant" To "CREDIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun applicationReplayReturnsTheOriginalAndDoesNotIssueASecondCard() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(anotherAccountUid())))
        and(issueCardReturns(anIssueCardResponse().withCardId(someCardUid()).withAccountId(anotherAccountUid()).withVariant("CREDIT")))
        and(theNextGeneratedUuidWillBe(anotherAccountUid(), anotherDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("50000002"))
        and(nowIs("2026-07-12T10:30:00Z"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/${anotherAccountHolderUid()}")
                .header("Idempotency-Key", "cc-application-replay")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(anotherUserId(), anotherAccountHolderUid())
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/${anotherAccountHolderUid()}")
                .header("Idempotency-Key", "cc-application-replay")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(anotherUserId(), anotherAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "account" To json {
                    "accountUid" To anotherAccountUid().toString()
                    "accountType" To "CREDIT_CARD"
                    "defaultCategory" To anotherDefaultCategory().toString()
                    "currency" To "GBP"
                    "createdAt" To "2026-07-12T10:30:00Z"
                    "name" To "Credit Card"
                    noExtraProps()
                }
                "card" To json {
                    "cardId" To someCardUid().toString()
                    "accountId" To anotherAccountUid().toString()
                    "maskedPan" To "************0000"
                    "variant" To "CREDIT"
                    "status" To "ACTIVE"
                    "expiry" To "10/29"
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun creditCardAccountAppearsInTheAccountListWithItsType() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = listAccount,
            accountHolderId = listHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), listHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accounts" To listOf(
                    json {
                        "accountUid" To listAccount.toString()
                        "accountType" To "CREDIT_CARD"
                        "defaultCategory" To something
                        "currency" To "GBP"
                        "createdAt" To something
                        "name" To "Credit Card"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun creditSummaryForAFreshAccountShowsTheFullLimitAvailableAndNothingOwed() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = freshAccount,
            accountHolderId = summaryHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(freshAccount)
            .withLedgerBalanceMinorUnits(0)
            .withHeldAmountMinorUnits(0)
            .withAvailableBalanceMinorUnits(0)))
        and(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(0)
            .withMinimumPaymentMinorUnits(0)
            .withDueDate("2026-11-21")))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$freshAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250000
                "owedMinorUnits" To 0
                "availableCreditMinorUnits" To 250000
                "statementBalanceMinorUnits" To 0
                "minimumPaymentMinorUnits" To 0
                "dueDate" To "2026-11-21"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/statement-figures?closingBalanceMinorUnits=0&cycleMonth=2026-10"
        }) {
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
        }
    }

    @Test
    fun creditSummaryCountsActiveHoldsAgainstAvailableCreditWithNothingOwed() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = holdsAccount,
            accountHolderId = summaryHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(holdsAccount)
            .withLedgerBalanceMinorUnits(0)
            .withHeldAmountMinorUnits(5_000)
            .withAvailableBalanceMinorUnits(-5_000)))
        and(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(0)
            .withMinimumPaymentMinorUnits(0)
            .withDueDate("2026-11-21")))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$holdsAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250000
                "owedMinorUnits" To 0
                "availableCreditMinorUnits" To 245000
                "statementBalanceMinorUnits" To 0
                "minimumPaymentMinorUnits" To 0
                "dueDate" To "2026-11-21"
                noExtraProps()
            }
        }
    }

    @Test
    fun creditSummaryReflectsSettledSpendAsOwedAndReducedAvailableCredit() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = settledAccount,
            accountHolderId = summaryHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(settledAccount)
            .withLedgerBalanceMinorUnits(-10_000)
            .withHeldAmountMinorUnits(0)
            .withAvailableBalanceMinorUnits(-10_000)))
        and(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(10_000)
            .withMinimumPaymentMinorUnits(500)
            .withDueDate("2026-11-21")))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$settledAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250000
                "owedMinorUnits" To 10000
                "availableCreditMinorUnits" To 240000
                "statementBalanceMinorUnits" To 10000
                "minimumPaymentMinorUnits" To 500
                "dueDate" To "2026-11-21"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/statement-figures?closingBalanceMinorUnits=-10000&cycleMonth=2026-10"
        }) {
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
        }
    }

    @Test
    fun creditSummaryReflectsAnOverpaymentAsAPositiveCreditBalanceWithNothingOwedAndNoMinimum() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = overpaidAccount,
            accountHolderId = summaryHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(overpaidAccount)
            .withLedgerBalanceMinorUnits(5_000)
            .withHeldAmountMinorUnits(0)
            .withAvailableBalanceMinorUnits(5_000)))
        and(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(-5_000)
            .withMinimumPaymentMinorUnits(0)
            .withDueDate("2026-11-21")))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$overpaidAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250000
                "owedMinorUnits" To -5000
                "availableCreditMinorUnits" To 255000
                "statementBalanceMinorUnits" To -5000
                "minimumPaymentMinorUnits" To 0
                "dueDate" To "2026-11-21"
                noExtraProps()
            }
        }
    }

    @Test
    fun creditSummaryFailsOpenReturningTheLedgerPositionWithoutStatementFiguresWhenCardServiceIsUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = failOpenAccount,
            accountHolderId = summaryHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(failOpenAccount)
            .withLedgerBalanceMinorUnits(-10_000)
            .withHeldAmountMinorUnits(0)
            .withAvailableBalanceMinorUnits(-10_000)))
        and(getStatementFiguresReturnsServerError())

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$failOpenAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "creditLimitMinorUnits" To 250000
                "owedMinorUnits" To 10000
                "availableCreditMinorUnits" To 240000
                noExtraProps()
            }
        }
    }
}
