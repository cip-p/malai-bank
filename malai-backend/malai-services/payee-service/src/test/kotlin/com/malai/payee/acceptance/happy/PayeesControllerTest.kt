package com.malai.payee.acceptance.happy

import com.malai.payee.acceptance.PayeesAcceptanceTest
import com.malai.payee.builders.request.PayeeRequestBuilder.Companion.aPayeeRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.*
import java.time.Instant
import java.util.UUID.randomUUID

/**
 * Plan 02-14 happy paths for `/api/v1/payees` (BACK-08).
 *
 * Mutating endpoints (POST/DELETE) write a `PAYEE_ADDED` / `PAYEE_REMOVED` audit row
 * inside the same `@Transactional` as the JPA write (D-2.13: `record()` REQUIRED).
 *
 * Matching sad-path coverage (validation errors, duplicates, cross-tenant 404)
 * lives in [com.malai.payee.acceptance.sad.PayeesControllerTest].
 *
 * NOT covered (Plan 02-14 deviation, deferred to Plan 02-16): support_agent → 403.
 * Plan 02-08 deferred `@EnableMethodSecurity`, so `@PreAuthorize("hasRole('CUSTOMER')")`
 * is decoration-only at runtime today.
 */
class PayeesControllerTest : PayeesAcceptanceTest() {

    @Test
    fun getReturnsHoldersPayeesOrderedByCreatedAtDesc() = malaiAcceptanceTest {
        given(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Mum",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            nickname = "Mum's birthday account",
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            createdBy = someUserId(),
        ))
        and(aPayeeExists(
            payeeId = anotherPayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "22222222",
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
            createdBy = someUserId(),
        ))
        // A payee belonging to a different holder — must NOT appear in the response.
        and(aPayeeExists(
            payeeId = randomUUID(),
            accountHolderId = anotherAccountHolderUid(),
            name = "Other holder's payee",
            sortCode = "30-30-30",
            accountNumber = "33333333",
            createdBy = anotherUserId(),
        ))

        whenever(payeeServiceReceives(
            get()
                .url("/api/v1/payees")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payees" To listOf(
                    json {
                        "payeeId" To anotherPayeeUid().toString()
                        "name" To "Landlord"
                        "sortCode" To "20-00-00"
                        "accountNumber" To "22222222"
                        "createdAt" To "2026-05-01T09:00:00Z"
                        noExtraProps()
                    },
                    json {
                        "payeeId" To somePayeeUid().toString()
                        "name" To "Mum"
                        "sortCode" To "12-34-56"
                        "accountNumber" To "11111111"
                        "nickname" To "Mum's birthday account"
                        "createdAt" To "2026-04-01T09:00:00Z"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun getReturnsEmptyListWhenHolderHasNoPayees() = malaiAcceptanceTest {
        whenever(payeeServiceReceives(
            get()
                .url("/api/v1/payees")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payees" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun postCreatesPayeeReturns201AndWritesAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-05-10T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePayeeUid()))

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "create-payee-key")
                .header("Content-Type", "application/json")
                .body(aPayeeRequest()
                    .withName("Mum")
                    .withSortCode("12-34-56")
                    .withAccountNumber("11111111")
                    .withNickname("Mum's birthday account"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "payeeId" To somePayeeUid().toString()
                "name" To "Mum"
                "sortCode" To "12-34-56"
                "accountNumber" To "11111111"
                "nickname" To "Mum's birthday account"
                "createdAt" To "2026-05-10T10:30:00Z"
                noExtraProps()
            }
        }

        and(thePayeesInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 1
            this[0].payeeId shouldBe somePayeeUid()
            this[0].accountHolderId shouldBe someAccountHolderUid()
            this[0].name shouldBe "Mum"
            this[0].sortCode shouldBe "12-34-56"
            this[0].accountNumber shouldBe "11111111"
            this[0].nickname shouldBe "Mum's birthday account"
            this[0].createdBy shouldBe someUserId()
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYEE_ADDED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Payee"
            this[0]["resource_id"] shouldBe somePayeeUid().toString()
        }
    }

    @Test
    fun postReplayWithSameKeyReturnsCachedResponseAndNoSecondPayeeOrAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(nowIs("2026-05-10T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(somePayeeUid()))

        val request = aPayeeRequest()
            .withName("Mum")
            .withSortCode("12-34-56")
            .withAccountNumber("11111111")

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "payee-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(payeeServiceReceives(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", "payee-replay-key")
                .header("Content-Type", "application/json")
                .body(request)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
        }

        // Replay invariant: only ONE payee row in the DB, despite two POSTs.
        and(thePayeesInTheDatabaseFor(someAccountHolderUid())) {
            this shouldHaveSize 1
            this[0].payeeId shouldBe somePayeeUid()
        }

        // And only ONE PAYEE_ADDED audit row.
        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYEE_ADDED"
        }
    }

    @Test
    fun deleteRemovesPayeeReturns204AndWritesAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Mum",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            createdBy = someUserId(),
        ))

        whenever(payeeServiceReceives(
            delete()
                .url("/api/v1/payees/${somePayeeUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(thePayeeInTheDatabase(somePayeeUid())) {
            this shouldBe null
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "PAYEE_REMOVED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Payee"
            this[0]["resource_id"] shouldBe somePayeeUid().toString()
        }
    }

    @Test
    fun deletePayeeReferencedOnlyByCancelledStandingOrderReturns204AndNullsPayeeLink() = malaiAcceptanceTest {
        val standingOrderId = randomUUID()
        given(theAuditChainIsAtGenesis())
        and(aPayeeExists(
            payeeId = somePayeeUid(),
            accountHolderId = someAccountHolderUid(),
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "12345678",
            createdBy = someUserId(),
        ))
        and(anAccountAndStandingOrderExistForPayee(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = standingOrderId,
            payeeId = somePayeeUid(),
            status = "CANCELLED",
            cancelledAt = Instant.parse("2026-06-15T10:00:00Z"),
        ))

        whenever(payeeServiceReceives(
            delete()
                .url("/api/v1/payees/${somePayeeUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(thePayeeInTheDatabase(somePayeeUid())) {
            this shouldBe null
        }

        and(theStandingOrderRowInTheDatabase(standingOrderId)) {
            this["payee_id"] shouldBe null
            this["status"] shouldBe "CANCELLED"
        }
    }
}
