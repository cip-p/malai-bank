package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.client.malai.accounts.builders.request.CreateAccountRequestBuilder.Companion.aDefaultCreateAccountRequest
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.Companion.anEntriesResponse
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder.EntryBuilder.Companion.anEntry
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.*
import java.util.UUID

class AccountsControllerTest : AccountsAcceptanceTest() {

    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")

    @Test
    fun getAccountsReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun getAccountsReturns403WhenTokenCarriesNoAccountHolderContext() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
                .withJwtTokenForUserId(someUserId(), listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
            body containsProp json {
                "message" To "4999"
                noExtraProps()
            }
        }
    }

    @Test
    fun getBalanceReturns404WhenAccountNotFound() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/balance")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getIdentifiersReturns404WhenAccountNotFound() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/identifiers")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsReturns404WhenAccountNotFound() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getTransactionsReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementReturns404WhenAccountNotFound() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getStatementReturns404WhenYyyymmIsMalformed() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/statements/2026-13")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSpendingReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/spending")
                .queryParam("month", "2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSpendingFailsClosedWith503WhenInsightsUnavailable() = malaiAcceptanceTest {
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
                    .withMerchantName("Tesco — weekly shop"),
            )))
        and(spendingBreakdownReturnsServerError())

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/spending")
                .queryParam("month", "2026-05")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe SERVICE_UNAVAILABLE
            body containsProp json {
                "message" To "3998"
                noExtraProps()
            }
        }
    }

    @Test
    fun overrideCategoryReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            put()
                .url("/api/v1/accounts/${someAccountUid()}/transactions/$someEntryId/category")
                .body("""{"category":"GROCERIES"}""")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun overrideCategoryReturns400WhenCategoryIsUnknown() = malaiAcceptanceTest {
        given(anAccountExists(accountId = someAccountUid(), accountHolderId = someAccountHolderUid()))

        whenever(accountServiceReceives(
            put()
                .url("/api/v1/accounts/${someAccountUid()}/transactions/$someEntryId/category")
                .body("""{"category":"NONSENSE"}""")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7969"
                noExtraProps()
            }
        }
    }

    @Test
    fun clearCategoryOverrideReturns404WhenAccountBelongsToAnotherAccountHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            userId = anotherUserId(),
        ))

        whenever(accountServiceReceives(
            delete()
                .url("/api/v1/accounts/${someAccountUid()}/transactions/$someEntryId/category")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun confirmationOfFundsReturns404WhenAccountNotFound() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/${someAccountUid()}/confirmation-of-funds")
                .queryParam("targetAmountInMinorUnits", "50000")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }

    @Test
    fun createPrimaryAccountWhenPrimaryAlreadyExists() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("PRIMARY"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6998"
                noExtraProps()
            }
        }
    }

    @Test
    fun createNonPrimaryAccountWhenNoPrimaryExists() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            post()
                .url("/api/v1/accounts")
                .body(aDefaultCreateAccountRequest()
                    .withAccountType("ADDITIONAL"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6997"
                noExtraProps()
            }
        }
    }

}
