package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.builders.request.ConfirmationOfPayeeRequestBuilder.Companion.aConfirmationOfPayeeRequest
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.client.malai.stub.cop.builders.response.StubCopLookupResponseBuilder.Companion.aStubCopLookupResponse
import com.malai.commons.banking.MalaiSortCode
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.jdbc.core.JdbcTemplate

class ConfirmationOfPayeeControllerTest : AccountsAcceptanceTest() {

    private val malaiSortCode = MalaiSortCode.CANONICAL

    private fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    @Test
    fun postReturnsMatchOnExactNameSortCodeAndAccountNumber() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Tesco",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "33445566",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-match-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Tesco")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("33445566"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun postReturnsMatchWhenLevenshteinDistanceIsWithinTypoTolerance() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Tesco",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "44556677",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-typo-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Tesko")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("44556677"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsCloseMatchWithOnRecordNameWhenSimilarityIsAboveThreshold() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Octopus Energy Ltd",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "11223344",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-close-match-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Octopus Energy Co Ltd")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("11223344"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "CLOSE_MATCH"
                "onRecordName" To "Octopus Energy Ltd"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchAndDoesNotLeakOnRecordNameWhenSimilarityIsInTheClosedLeakRange() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Sarah Mitchell",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "11335577",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-leak-closed-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("S Mitchell")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("11335577"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchAndDoesNotLeakOnRecordNameWhenSubmittedNameIsTooDissimilar() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Sarah Mitchell",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "22334455",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-anti-fishing-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("zzz")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("22334455"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenStubAlsoHasNoRecordForTheSortCodeAndAccountNumber() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-no-match-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Unknown")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("99999999"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postFederatesToStubAndReturnsMatchWhenStubKnowsTheDestination() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-fed-match-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Alice Walker")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("88990011"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postFederatesToStubAndReturnsCloseMatchWithOnRecordNameFromStub() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(
            aStubCopLookupResponse()
                .withVerdict("CLOSE_MATCH")
                .withOnRecordName("Octopus Energy Ltd")
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-fed-close-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Octopus Energy Co Ltd")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("88990022"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "CLOSE_MATCH"
                "onRecordName" To "Octopus Energy Ltd"
                noExtraProps()
            }
        }
    }

    @Test
    fun postDoesNotFederateToStubWhenLocalDirectoryHasAMatch() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Local Match",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "17171717",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-local-only-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Local Match")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("17171717"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsMatchAcrossDifferentHoldersToVerifyStanceYUnscopedDirectory() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = anotherUserId(),
            name = "Alice Smith",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "55667788",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-cross-holder-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Alice Smith")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("55667788"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenAccountTypeIsLoan() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.LOAN,
            userId = someUserId(),
            name = "Personal Loan",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "12121212",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-loan-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Personal Loan")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("12121212"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenAccountTypeIsFixedTermDeposit() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.FIXED_TERM_DEPOSIT,
            userId = someUserId(),
            name = "1Y Fixed Deposit",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "13131313",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-ftd-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("1Y Fixed Deposit")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("13131313"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenAccountTypeIsCreditCard() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.CREDIT_CARD,
            userId = someUserId(),
            name = "Credit Card",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "90000042",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-credit-card-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Credit Card")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("90000042"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenAccountStatusIsPending() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Pending Personal",
            status = PENDING,
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "14141414",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-pending-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Pending Personal")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("14141414"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchWhenIdentifierTypeIsNotSortCode() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Personal",
            status = ACTIVE,
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = IBAN_BIC,
            bankIdentifier = "MIDLGB22XXX",
            accountIdentifier = "GB29MIDL12345612345678",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-iban-key")
                .header("Content-Type", "application/json")
                .body(aConfirmationOfPayeeRequest()
                    .withName("Personal")
                    .withSortCode(malaiSortCode)
                    .withAccountNumber("15151515"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "NO_MATCH"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReplayWithSameKeyReturnsCachedResponse() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = AccountType.PRIMARY,
            userId = someUserId(),
            name = "Tesco",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = malaiSortCode,
            accountIdentifier = "16161616",
        ))
        and(thereIsNoIdempotencyKeyInTheDatabase())

        val request = aConfirmationOfPayeeRequest()
            .withName("Tesco")
            .withSortCode(malaiSortCode)
            .withAccountNumber("16161616")

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "cop-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
    }
}
