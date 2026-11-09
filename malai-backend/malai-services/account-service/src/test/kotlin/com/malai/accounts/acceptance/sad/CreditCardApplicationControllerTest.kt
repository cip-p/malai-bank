package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountType
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.StateCollector
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

class CreditCardApplicationControllerTest : AccountsAcceptanceTest() {

    private val rejectHolder = UUID.fromString("00000000-0000-4000-8802-000000000001")
    private val rejectExistingAccount = UUID.fromString("00000000-0000-4000-8802-000000000002")
    private val rollbackHolder = UUID.fromString("00000000-0000-4000-8802-000000000003")
    private val rollbackAccount = UUID.fromString("00000000-0000-4000-8802-000000000004")
    private val foreignHolder = UUID.fromString("00000000-0000-4000-8802-000000000005")
    private val crossHolderA = UUID.fromString("00000000-0000-4000-8802-000000000006")
    private val crossAccountA = UUID.fromString("00000000-0000-4000-8802-000000000007")
    private val crossHolderB = UUID.fromString("00000000-0000-4000-8802-000000000008")
    private val summaryNonCreditHolder = UUID.fromString("00000000-0000-4000-8802-000000000009")
    private val summaryPrimaryAccount = UUID.fromString("00000000-0000-4000-8802-00000000000a")
    private val summaryOwner = UUID.fromString("00000000-0000-4000-8802-00000000000b")
    private val summaryForeignAccount = UUID.fromString("00000000-0000-4000-8802-00000000000c")
    private val summaryStranger = UUID.fromString("00000000-0000-4000-8802-00000000000d")

    private fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    private fun theNumberOfAccountsInTheDatabaseWithId(accountId: UUID) = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForObject(
            "SELECT COUNT(*) FROM accounts WHERE account_id = ?",
            Long::class.java,
            accountId,
        )
    }

    @Test
    fun applicationIsRejectedWhenTheHolderAlreadyHasACreditCardAccount() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(anAccountExists(
            accountId = rejectExistingAccount,
            accountHolderId = rejectHolder,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/$rejectHolder")
                .header("Idempotency-Key", "cc-reject-existing")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), rejectHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6992"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun applicationLeavesNoAccountWhenCardIssuanceFails() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(rollbackAccount)))
        and(issueCardReturnsServerError())
        and(theNextGeneratedUuidWillBe(rollbackAccount, someDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("50000010"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/$rollbackHolder")
                .header("Idempotency-Key", "cc-rollback")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), rollbackHolder)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe INTERNAL_SERVER_ERROR
        }

        and(theNumberOfAccountsInTheDatabaseWithId(rollbackAccount)) {
            this shouldBe 0L
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun applicationForAnotherHoldersIdInThePathReturns404() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/$foreignHolder")
                .header("Idempotency-Key", "cc-foreign-path")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun applicationWithoutAnIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/${someAccountHolderUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
            }
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun anIdempotencyKeyReusedAcrossHoldersIsAPayloadMismatchNotACrossTenantReplay() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(crossAccountA)))
        and(issueCardReturns(anIssueCardResponse().withAccountId(crossAccountA).withVariant("CREDIT")))
        and(theNextGeneratedUuidWillBe(crossAccountA, someDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("50000011"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/$crossHolderA")
                .header("Idempotency-Key", "cc-cross-tenant")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), crossHolderA)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/credit-card-applications/$crossHolderB")
                .header("Idempotency-Key", "cc-cross-tenant")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(anotherUserId(), crossHolderB)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7990"
            }
        }

        and(theNumberOfAccountsInTheDatabaseWithId(crossAccountA)) {
            this shouldBe 1L
        }

        and(accountServiceMadeTheFollowingHttpRequests {
            url == "/card-service/api/v1/internal/cards"
        }) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun creditSummaryForANonCreditAccountReturns404() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = summaryPrimaryAccount,
            accountHolderId = summaryNonCreditHolder,
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Personal",
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$summaryPrimaryAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryNonCreditHolder)
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
    fun creditSummaryForAnAccountOwnedByAnotherHolderReturns404() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = summaryForeignAccount,
            accountHolderId = summaryOwner,
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts/$summaryForeignAccount/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), summaryStranger)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
                noExtraProps()
            }
        }
    }
}
