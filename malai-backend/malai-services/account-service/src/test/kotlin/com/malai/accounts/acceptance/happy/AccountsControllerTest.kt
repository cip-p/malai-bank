package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.client.malai.accounts.builders.request.CreateAccountRequestBuilder.Companion.aDefaultCreateAccountRequest
import com.malai.client.malai.insights.builders.response.CategorisationsResponseBuilder.CategorisationBuilder.Companion.aCategorisation
import com.malai.client.malai.insights.builders.response.CategorisationsResponseBuilder.Companion.aCategorisationsResponse
import com.malai.client.malai.insights.builders.response.SpendingBreakdownResponseBuilder.CategoryTotalBuilder.Companion.aCategoryTotal
import com.malai.client.malai.insights.builders.response.SpendingBreakdownResponseBuilder.Companion.aSpendingBreakdownResponse
import com.malai.client.malai.card.builders.response.AccountAuthorisationResponseBuilder.Companion.anAccountAuthorisation
import com.malai.client.malai.card.builders.response.AccountAuthorisationsResponseBuilder.Companion.anAccountAuthorisations
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.card.builders.response.StatementFiguresResponseBuilder.Companion.statementFigures
import io.kotest.matchers.collections.shouldBeEmpty
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder.Companion.aBalancesResponse
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.Companion.anEntriesResponse
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.EntryBuilder.Companion.anEntry
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.commons.model.Currency.GBP
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpMethod.PUT
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID
import java.util.UUID.randomUUID

class AccountsControllerTest : AccountsAcceptanceTest() {

    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000a1")
    private val anotherEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000a2")

    @Test
    fun getAccounts() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            defaultCategory = someDefaultCategory(),
            currency = GBP,
            createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            name = "Personal",
            userId = someUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accounts" To listOf(
                    json {
                        "accountUid" To someAccountUid().toString()
                        "accountType" To "PRIMARY"
                        "defaultCategory" To someDefaultCategory().toString()
                        "currency" To someCurrency()
                        "createdAt" To "2026-10-15T10:30:00Z"
                        "name" To "Personal"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getAccountsFiltersSuspenseAccounts() = malaiAcceptanceTest {
        val suspenseAccountId = randomUUID()
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            currency = GBP,
            name = "Personal",
            userId = someUserId(),
        ))
        and(anAccountExists(
            accountId = suspenseAccountId,
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.SUSPENSE,
            currency = GBP,
            name = "Suspense",
            userId = someUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accounts" To listOf(
                    json {
                        "accountUid" To someAccountUid().toString()
                        "accountType" To "PRIMARY"
                    }
                )
            }
        }
    }

    @Test
    fun getBalanceComposesLedgerAvailableAndHeldFromTheLedger() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(10_000L)
            .withHeldAmountMinorUnits(2_500L)
            .withAvailableBalanceMinorUnits(7_500L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/balance")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 10000
                "availableBalanceMinorUnits" To 7500
                "heldAmountMinorUnits" To 2500
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/balances"
        }) {
            method shouldBe GET
        }
    }

    @Test
    fun getBalanceProjectsZeroForANewAccount() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(0L)
            .withHeldAmountMinorUnits(0L)
            .withAvailableBalanceMinorUnits(0L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/balance")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 0
                "availableBalanceMinorUnits" To 0
                "heldAmountMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsComposesLedgerEntriesWithInsightsCategories() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(500L)
                    .withRunningBalanceMinorUnits(8_001L)
                    .withDescription("some-description")
                    .withCounterpartyId(someAccountUid())
                    .withMerchantName("some-merchant-name")
                    .withCategory("ledger-category-is-ignored")
                    .withTransactionType("PAYMENT_CREDIT"),
                anEntry()
                    .withEntryId(anotherEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-02T11:00:00Z")
                    .withAmountMinorUnits(-2_500L)
                    .withRunningBalanceMinorUnits(7_501L)
                    .withTransactionType("PAYMENT"),
            )))
        and(categoriseReturns(aCategorisationsResponse().withCategorisations(
            aCategorisation().withEntryId(someEntryId).withCategory("GROCERIES"),
            aCategorisation().withEntryId(anotherEntryId).withCategory("GENERAL"),
        )))
        and(listAccountAuthorisationsReturns(anAccountAuthorisations()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 500
                        "runningBalanceMinorUnits" To 8001
                        "description" To "some-description"
                        "counterpartyId" To someAccountUid().toString()
                        "merchantName" To "some-merchant-name"
                        "category" To "GROCERIES"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-02T11:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -2500
                        "runningBalanceMinorUnits" To 7501
                        "category" To "GENERAL"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/entries"
        }) {
            method shouldBe GET
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/categorisations"
        }) {
            method shouldBe POST
            headers["Authorization"]!! shouldStartWith "Bearer "
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "entries" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "amountMinorUnits" To 500
                        "merchantName" To "some-merchant-name"
                        "description" To "some-description"
                        "transactionType" To "PAYMENT_CREDIT"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "amountMinorUnits" To -2500
                        "transactionType" To "PAYMENT"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsFailsOpenWithNullCategoriesWhenInsightsIsUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(500L)
                    .withRunningBalanceMinorUnits(8_001L)
                    .withMerchantName("some-merchant-name"),
            )))
        and(categoriseReturnsServerError())
        and(listAccountAuthorisationsReturns(anAccountAuthorisations()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 500
                        "runningBalanceMinorUnits" To 8001
                        "merchantName" To "some-merchant-name"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsReturnsEmptyListWhenLedgerHasNoEntries() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse().withAccountId(someAccountUid())))
        and(listAccountAuthorisationsReturns(anAccountAuthorisations()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsMergesPendingAndDeclinedCardRowsWithLedgerEntriesNewestFirst() = malaiAcceptanceTest {
        val pendingAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")
        val declinedAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000c2")

        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(500L)
                    .withRunningBalanceMinorUnits(8_001L)
                    .withMerchantName("some-merchant-name")
                    .withTransactionType("PAYMENT_CREDIT"),
                anEntry()
                    .withEntryId(anotherEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-02T11:00:00Z")
                    .withAmountMinorUnits(-2_500L)
                    .withRunningBalanceMinorUnits(7_501L)
                    .withTransactionType("PAYMENT"),
            )))
        and(categoriseReturns(aCategorisationsResponse().withCategorisations(
            aCategorisation().withEntryId(someEntryId).withCategory("GROCERIES"),
            aCategorisation().withEntryId(anotherEntryId).withCategory("GENERAL"),
        )))
        and(listAccountAuthorisationsReturns(anAccountAuthorisations().withAuthorisations(
            anAccountAuthorisation()
                .withAuthorisationId(pendingAuthorisationId)
                .withCardId(someCardUid())
                .withStatus("AUTHORISED")
                .withMerchantName("Fictional Coffee Co")
                .withAmountMinorUnits(4_370)
                .withCurrency("GBP")
                .withDeclineReason(null)
                .withCreatedAt("2026-05-04T09:00:00Z"),
            anAccountAuthorisation()
                .withAuthorisationId(declinedAuthorisationId)
                .withCardId(someCardUid())
                .withStatus("DECLINED")
                .withMerchantName("Fictional Books Ltd")
                .withAmountMinorUnits(1_599)
                .withCurrency("GBP")
                .withDeclineReason("CARD_FROZEN")
                .withCreatedAt("2026-05-03T18:00:00Z"),
        )))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To listOf(
                    json {
                        "entryId" To pendingAuthorisationId.toString()
                        "transactionId" To pendingAuthorisationId.toString()
                        "postedAt" To "2026-05-04T09:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -4370
                        "runningBalanceMinorUnits" To 8001
                        "merchantName" To "Fictional Coffee Co"
                        "status" To "PENDING"
                        "cardId" To someCardUid().toString()
                        noExtraProps()
                    },
                    json {
                        "entryId" To declinedAuthorisationId.toString()
                        "transactionId" To declinedAuthorisationId.toString()
                        "postedAt" To "2026-05-03T18:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -1599
                        "runningBalanceMinorUnits" To 8001
                        "merchantName" To "Fictional Books Ltd"
                        "status" To "DECLINED"
                        "declineReason" To "CARD_FROZEN"
                        "cardId" To someCardUid().toString()
                        noExtraProps()
                    },
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 500
                        "runningBalanceMinorUnits" To 8001
                        "merchantName" To "some-merchant-name"
                        "category" To "GROCERIES"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-02T11:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -2500
                        "runningBalanceMinorUnits" To 7501
                        "category" To "GENERAL"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations?accountId=${someAccountUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"]!! shouldStartWith "Bearer "
        }
    }

    @Test
    fun getTransactionsFailsOpenReturningLedgerEntriesOnlyWhenCardServiceIsUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(500L)
                    .withRunningBalanceMinorUnits(8_001L)
                    .withMerchantName("some-merchant-name"),
            )))
        and(categoriseReturns(aCategorisationsResponse().withCategorisations(
            aCategorisation().withEntryId(someEntryId).withCategory("GROCERIES"),
        )))
        and(listAccountAuthorisationsReturnsServerError())

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-03T12:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 500
                        "runningBalanceMinorUnits" To 8001
                        "merchantName" To "some-merchant-name"
                        "category" To "GROCERIES"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementComposesOpeningClosingAndInPeriodEntries() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withOpeningBalanceMinorUnits(20_000L)
            .withClosingBalanceMinorUnits(27_500L)
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-15T11:00:00Z")
                    .withAmountMinorUnits(-2_500L)
                    .withRunningBalanceMinorUnits(27_500L),
                anEntry()
                    .withEntryId(anotherEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-01T10:00:00Z")
                    .withAmountMinorUnits(10_000L)
                    .withRunningBalanceMinorUnits(30_000L),
            )))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "periodStart" To "2026-05-01T00:00:00Z"
                "periodEnd" To "2026-06-01T00:00:00Z"
                "currency" To "GBP"
                "openingBalanceMinorUnits" To 20000
                "closingBalanceMinorUnits" To 27500
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-15T11:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -2500
                        "runningBalanceMinorUnits" To 27500
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-01T10:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 10000
                        "runningBalanceMinorUnits" To 30000
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url.startsWith("/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        }) {
            method shouldBe GET
            url.contains("periodStart=") shouldBe true
            url.contains("periodEnd=") shouldBe true
        }
    }

    @Test
    fun getStatementReturnsEmptyMonthWithClosingEqualToOpening() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withOpeningBalanceMinorUnits(7_500L)
            .withClosingBalanceMinorUnits(7_500L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "periodStart" To "2026-05-01T00:00:00Z"
                "periodEnd" To "2026-06-01T00:00:00Z"
                "currency" To "GBP"
                "openingBalanceMinorUnits" To 7500
                "closingBalanceMinorUnits" To 7500
                "transactions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementForACreditCardAccountComposesTheCycleFiguresFromTheClosingPosition() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.CREDIT_CARD,
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withOpeningBalanceMinorUnits(0L)
            .withClosingBalanceMinorUnits(-40_000L)
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-15T11:00:00Z")
                    .withAmountMinorUnits(-40_000L)
                    .withRunningBalanceMinorUnits(-40_000L)
                    .withMerchantName("Fictional Coffee Co"),
            )))
        and(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(40_000)
            .withMinimumPaymentMinorUnits(1_000)
            .withDueDate("2026-06-21")))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "periodStart" To "2026-05-01T00:00:00Z"
                "periodEnd" To "2026-06-01T00:00:00Z"
                "currency" To "GBP"
                "openingBalanceMinorUnits" To 0
                "closingBalanceMinorUnits" To -40000
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransactionId().toString()
                        "postedAt" To "2026-05-15T11:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To -40000
                        "runningBalanceMinorUnits" To -40000
                        "merchantName" To "Fictional Coffee Co"
                        noExtraProps()
                    },
                )
                "statementBalanceMinorUnits" To 40000
                "minimumPaymentMinorUnits" To 1000
                "dueDate" To "2026-06-21"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/statement-figures?closingBalanceMinorUnits=-40000&cycleMonth=2026-05"
        }) {
            method shouldBe GET
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
        }
    }

    @Test
    fun getStatementForACreditCardAccountFailsOpenReturningEntriesWithoutFiguresWhenCardServiceIsUnavailable() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.CREDIT_CARD,
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withOpeningBalanceMinorUnits(0L)
            .withClosingBalanceMinorUnits(-40_000L)))
        and(getStatementFiguresReturnsServerError())

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "periodStart" To "2026-05-01T00:00:00Z"
                "periodEnd" To "2026-06-01T00:00:00Z"
                "currency" To "GBP"
                "openingBalanceMinorUnits" To 0
                "closingBalanceMinorUnits" To -40000
                "transactions" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsForACreditCardAccountShowsARepaymentAsACreditLedgerRow() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.CREDIT_CARD,
            name = "Credit Card",
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-20T09:00:00Z")
                    .withAmountMinorUnits(30_000L)
                    .withRunningBalanceMinorUnits(-10_000L)
                    .withDescription("Pay credit card")
                    .withCounterpartyId(anotherAccountUid())
                    .withTransactionType("PAYMENT_CREDIT"),
            )))
        and(categoriseReturnsServerError())
        and(listAccountAuthorisationsReturns(anAccountAuthorisations()))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "transactions" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "transactionId" To someTransferUid().toString()
                        "postedAt" To "2026-05-20T09:00:00Z"
                        "currency" To "GBP"
                        "amountMinorUnits" To 30000
                        "runningBalanceMinorUnits" To -10000
                        "description" To "Pay credit card"
                        "counterpartyId" To anotherAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getSpendingComposesInsightsBreakdownWithAccountMetadata() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse()
            .withAccountId(someAccountUid())
            .withEntries(
                anEntry()
                    .withEntryId(someEntryId)
                    .withTransactionId(someTransactionId())
                    .withPostedAt("2026-05-03T12:00:00Z")
                    .withAmountMinorUnits(-3_000L)
                    .withRunningBalanceMinorUnits(5_000L)
                    .withMerchantName("Tesco — weekly shop")
                    .withTransactionType("PAYMENT_SETTLEMENT"),
                anEntry()
                    .withEntryId(anotherEntryId)
                    .withTransactionId(someTransferUid())
                    .withPostedAt("2026-05-02T11:00:00Z")
                    .withAmountMinorUnits(-7_800L)
                    .withRunningBalanceMinorUnits(8_000L)
                    .withMerchantName("Octopus Energy — DD")
                    .withTransactionType("DIRECT_DEBIT_COLLECTION"),
            )))
        and(spendingBreakdownReturns(aSpendingBreakdownResponse()
            .withCategories(
                aCategoryTotal().withCategory("BILLS").withTotalMinorUnits(7_800L).withTransactionCount(1),
                aCategoryTotal().withCategory("GROCERIES").withTotalMinorUnits(4_200L).withTransactionCount(2),
            )
            .withTotalMinorUnits(12_000L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/spending")
                .queryParam("month", "2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To "2026-05"
                "currency" To "GBP"
                "categories" To listOf(
                    json { "category" To "BILLS"; "totalMinorUnits" To 7_800; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 4_200; "transactionCount" To 2; noExtraProps() },
                )
                "totalMinorUnits" To 12_000
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url.startsWith("/ledger-service/api/v1/internal/ledger/accounts/${someAccountUid()}/entries")
        }) {
            method shouldBe GET
            url.contains("periodStart=") shouldBe true
            url.contains("periodEnd=") shouldBe true
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/spending-breakdown"
        }) {
            method shouldBe POST
            headers["Authorization"]!! shouldStartWith "Bearer "
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "month" To "2026-05"
                "entries" To listOf(
                    json {
                        "entryId" To someEntryId.toString()
                        "amountMinorUnits" To -3_000
                        "merchantName" To "Tesco — weekly shop"
                        "transactionType" To "PAYMENT_SETTLEMENT"
                        noExtraProps()
                    },
                    json {
                        "entryId" To anotherEntryId.toString()
                        "amountMinorUnits" To -7_800
                        "merchantName" To "Octopus Energy — DD"
                        "transactionType" To "DIRECT_DEBIT_COLLECTION"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getSpendingReturnsEmptyBreakdownForAMonthWithNoSpending() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(listEntriesReturns(anEntriesResponse().withAccountId(someAccountUid())))
        and(spendingBreakdownReturns(aSpendingBreakdownResponse().withTotalMinorUnits(0L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/spending")
                .queryParam("month", "2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To "2026-05"
                "currency" To "GBP"
                "categories" To emptyList<Any>()
                "totalMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    @Test
    fun overrideCategoryForwardsThePutToInsightsAndWritesTheAuditRow() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theAuditChainIsAtGenesis())
        and(putOverrideReturns(someEntryId))

        whenever(accountServiceReceives(
            put()
                .url("/api/v1/accounts/${someAccountUid()}/transactions/$someEntryId/category")
                .body("""{"category":"EATING_OUT"}""")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/overrides/$someEntryId"
        }) {
            method shouldBe PUT
            headers["Authorization"]!! shouldStartWith "Bearer "
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "category" To "EATING_OUT"
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "CATEGORY_OVERRIDDEN"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "LedgerEntry"
            this[0]["resource_id"] shouldBe someEntryId.toString()
        }
    }

    @Test
    fun clearCategoryOverrideForwardsTheDeleteToInsightsAndWritesTheAuditRow() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theAuditChainIsAtGenesis())
        and(deleteOverrideReturns(someEntryId))

        whenever(accountServiceReceives(
            delete()
                .url("/api/v1/accounts/${someAccountUid()}/transactions/$someEntryId/category")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/insights-service/api/v1/internal/insights/overrides/$someEntryId"
        }) {
            method shouldBe DELETE
            headers["Authorization"]!! shouldStartWith "Bearer "
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "CATEGORY_OVERRIDE_CLEARED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "LedgerEntry"
            this[0]["resource_id"] shouldBe someEntryId.toString()
        }
    }

    @Test
    fun getIdentifiers() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = someBankIdentifier(),
            accountIdentifier = someAccountIdentifier(),
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = IBAN_BIC,
            bankIdentifier = anotherBankIdentifier(),
            accountIdentifier = anotherAccountIdentifier(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/identifiers")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountIdentifier" To someAccountIdentifier()
                "bankIdentifier" To someBankIdentifier()
                "iban" To anotherAccountIdentifier()
                "bic" To anotherBankIdentifier()
                "accountIdentifiers" To listOf(
                    json {
                        "identifierType" To "SORT_CODE"
                        "bankIdentifier" To someBankIdentifier()
                        "accountIdentifier" To someAccountIdentifier()
                        noExtraProps()
                    },
                    json {
                        "identifierType" To "IBAN_BIC"
                        "bankIdentifier" To anotherBankIdentifier()
                        "accountIdentifier" To anotherAccountIdentifier()
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun confirmationOfFundsWhenSufficient() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(100_000L)
            .withHeldAmountMinorUnits(0L)
            .withAvailableBalanceMinorUnits(100_000L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/confirmation-of-funds")
                .queryParam("targetAmountInMinorUnits", "50000")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "requestedAmountAvailableToSpend" To true
                "accountWouldBeInOverdraftIfRequestedAmountSpent" To false
                noExtraProps()
            }
        }
    }

    @Test
    fun confirmationOfFundsWhenInsufficient() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(95_000L)
            .withHeldAmountMinorUnits(0L)
            .withAvailableBalanceMinorUnits(95_000L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/confirmation-of-funds")
                .queryParam("targetAmountInMinorUnits", "100000")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "requestedAmountAvailableToSpend" To false
                "accountWouldBeInOverdraftIfRequestedAmountSpent" To true
                noExtraProps()
            }
        }
    }

    @Test
    fun confirmationOfFundsIsDerivedFromAvailableNotLedgerBalance() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(getBalancesReturns(aBalancesResponse()
            .withAccountId(someAccountUid())
            .withLedgerBalanceMinorUnits(100_000L)
            .withHeldAmountMinorUnits(60_000L)
            .withAvailableBalanceMinorUnits(40_000L)))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/confirmation-of-funds")
                .queryParam("targetAmountInMinorUnits", "50000")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "requestedAmountAvailableToSpend" To false
                "accountWouldBeInOverdraftIfRequestedAmountSpent" To true
                noExtraProps()
            }
        }
    }

    @Test
    fun createAccountIsSavedAsPrimary() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(someAccountUid())))
        and(issueCardReturns(anIssueCardResponse().withCardId(someCardUid()).withAccountId(someAccountUid())))
        and(theNextGeneratedUuidWillBe(someAccountUid(), someDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("87654321"))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("PRIMARY")
                    .withCurrency(someCurrency())
                    .withName("Personal"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "accountType" To "PRIMARY"
                "defaultCategory" To someDefaultCategory().toString()
                "currency" To someCurrency()
                "createdAt" To "2026-10-15T10:30:00Z"
                "name" To "Personal"
                noExtraProps()
            }
        }

        and(theAccountInTheDatabase(someAccountUid())) {
            this shouldBeEqualUsingFields AccountEntity(
                accountId = someAccountUid(),
                accountType = AccountType.PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = GBP,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                name = "Personal",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = ACTIVE,
            )
        }

        and(theIdentifiersInTheDatabase(someAccountUid())) {
            // sorted by enum ordinal → SORT_CODE then IBAN_BIC
            this shouldHaveSize 2

            this[0].identifierType shouldBe SORT_CODE
            this[0].accountId shouldBe someAccountUid()
            this[0].bankIdentifier shouldBe "65-82-37"
            this[0].accountIdentifier shouldBe "87654321"

            this[1].identifierType shouldBe IBAN_BIC
            this[1].accountId shouldBe someAccountUid()
            this[1].bankIdentifier shouldBe "MALAGB22XXX"
            // GB IBAN: GB + check + MALA(bank) + 658237(sort) + 87654321(account)
            this[1].accountIdentifier shouldBe "GB76MALA65823787654321"
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNT_CREATED"
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
                "guardPolicy" To "NO_OVERDRAFT"
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
                "cardholderName" To "Personal"
                "variant" To "DEBIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun createAdditionalAccountWhenPrimaryExists() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(randomUUID())))
        and(issueCardReturns(anIssueCardResponse()))
        and(theNextGeneratedUuidWillBe(randomUUID()))
        // Distinct primed number to avoid the (identifier_type, account_identifier) unique
        // constraint hitting the default "12345678" used by other tests in this class — DB
        // cleanup runs once per class via @BeforeAll, not per test.
        and(theNextGeneratedAccountNumberWillBe("11111111"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("ADDITIONAL")
                    .withCurrency(someCurrency())
                    .withName("Savings"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountType" To "ADDITIONAL"
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards"
        }) {
            method shouldBe POST
            body containsProp json {
                "accountId" To something
                "accountHolderId" To someAccountHolderUid().toString()
                "cardholderName" To "Savings"
                "variant" To "DEBIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun createSavingsAccountIssuesNoDebitCard() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(anotherAccountUid())))
        and(theNextGeneratedUuidWillBe(anotherAccountUid()))
        and(theNextGeneratedAccountNumberWillBe("44444444"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("SAVINGS")
                    .withCurrency(someCurrency())
                    .withName("Rainy Day"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountType" To "SAVINGS"
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun createAccountForADifferentAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(anotherAccountUid())))
        and(issueCardReturns(anIssueCardResponse().withAccountId(anotherAccountUid())))
        and(theNextGeneratedUuidWillBe(anotherAccountUid()))
        and(theNextGeneratedAccountNumberWillBe("22222222"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("PRIMARY"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(anotherUserId(), anotherAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To anotherAccountUid().toString()
                "accountType" To "PRIMARY"
            }
        }

        and(theAccountInTheDatabase(anotherAccountUid())) {
            accountType shouldBe AccountType.PRIMARY
            userId shouldBe anotherUserId()
            accountHolderId shouldBe anotherAccountHolderUid()
        }
    }
}
