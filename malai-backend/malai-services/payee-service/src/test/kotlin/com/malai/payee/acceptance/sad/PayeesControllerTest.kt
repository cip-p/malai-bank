package com.malai.payee.acceptance.sad

import com.malai.payee.acceptance.PayeesAcceptanceTest
import com.malai.payee.builders.request.PayeeRequestBuilder.Companion.aPayeeRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.*
import java.util.UUID.randomUUID

/**
 * Plan 02-14 sad paths for `/api/v1/payees` (BACK-08).
 *
 * Cross-tenant DELETE returns 404 (not 403) — same pattern as Plan 02-12 Transfers:
 * `repository.findByPayeeIdAndAccountHolderId(...) ?: throw NotFoundException(PAYEE_NOT_FOUND)`,
 * so other holders' payeeIds look identical to non-existent ones (no leakage of tenant
 * existence).
 *
 * Matching happy-path coverage lives in [com.malai.payee.acceptance.happy.PayeesControllerTest].
 */
class PayeesControllerTest : PayeesAcceptanceTest() {

    @Test
    fun getReturns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(payeeServiceReceives(
            get().url("/api/v1/payees")
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
    fun getReturns403WhenTokenCarriesNoAccountHolderContext() = malaiAcceptanceTest {
        whenever(payeeServiceReceives(
            get()
                .url("/api/v1/payees")
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
    fun getReturns403WhenTokenHasNoCustomerRole() = malaiAcceptanceTest {
        whenever(payeeServiceReceives(
            get()
                .url("/api/v1/payees")
                .withJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid(), listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postWithoutIdempotencyKeyReturns400() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Content-Type", "application/json")
                // no Idempotency-Key header
                .body(aPayeeRequest()
                    .withName("Mum")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "7991"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReplayWithDifferentBodyReturns422() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-05-10T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePayeeUid()))

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "payee-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Mum")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "payee-mismatch-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Different name")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7990"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenSortCodeIsInvalid() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "invalid-sort-code-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Mum")
                    .withSortCode("123456")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenAccountNumberIsNotEightDigits() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "invalid-account-number-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Mum")
                    .withSortCode("12-34-56")
                    .withAccountNumber("123"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturns400WhenNameIsBlank() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "blank-name-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }
    }

    @Test
    fun postDuplicatePayeeForSameHolderReturns409() = malaiAcceptanceTest {
        // V9 UNIQUE (account_holder_id, sort_code, account_number) is the gate.
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Mum",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            createdBy = someUserId(),
        ))
        and(theNextGeneratedUuidWillBe(randomUUID()))

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "duplicate-payee-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Mum (renamed)")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6999"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteUnknownPayeeReturns404PayeeNotFound() = malaiAcceptanceTest {
        whenever(payeeServiceReceives(
            delete()
                .url("/api/v1/payees/${somePayeeUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8995"
                noExtraProps()
            }
        }
    }

    @Test
    fun deleteAnotherHoldersPayeeReturns404NotForbidden() = malaiAcceptanceTest {
        // Cross-tenant masquerade: payee belongs to holder B; JWT for holder A.
        // PayeesService uses `findByPayeeIdAndAccountHolderId(...) ?: throw NotFoundException(PAYEE_NOT_FOUND)`,
        // so holder A sees the same 404 they'd see for a non-existent id — no existence leakage.
        given(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = anotherAccountHolderUid(),
            name = "Other holder's payee",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            createdBy = anotherUserId(),
        ))

        whenever(payeeServiceReceives(
            delete()
                .url("/api/v1/payees/${somePayeeUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8995"
                noExtraProps()
            }
        }
    }

    @Test
    fun deletePayeeReferencedByActiveStandingOrderReturns409() = malaiAcceptanceTest {
        given(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "12345678",
            createdBy = someUserId(),
        ))
        and(anAccountAndStandingOrderExistForPayee(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = randomUUID(),
            payeeId = somePayeeUid(),
            status = "ACTIVE",
        ))

        whenever(payeeServiceReceives(
            delete()
                .url("/api/v1/payees/${somePayeeUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6994"
                noExtraProps()
            }
        }

        and(thePayeeInTheDatabase(somePayeeUid())) {
            this shouldNotBe null
        }
    }
}
