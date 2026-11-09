package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.CaptureType.USER_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.fixtures.createPreconditionPayee
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * E2E coverage for the standing-order lifecycle against a running standing-order-service container.
 * One end-to-end ride: register a holder with an account → create a standing order against a saved
 * payee → list it (ACTIVE) → cancel it → list it again (CANCELLED, cancellation timestamp stamped).
 *
 * The saved payee is primed off-diagram so the sequence stays focused on standing-order calls.
 * `startDate` is computed a week ahead of the container's real clock (e2e produces real timestamps)
 * so it always clears the start-date-in-the-past guard; `nextPaymentAt` is born at the start of that
 * day in UTC.
 */
class StandingOrderTest : MalaiE2ETest() {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun standingOrder() = malaiTest {
        val startDate = LocalDate.now(UTC).plusDays(7)

        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        val payeeId = createPreconditionPayee(
            userId = userId,
            accountHolderId = accountHolderId,
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "12345678",
            idempotencyKey = "e2e-standing-order-payee",
        )

        whenever(standingOrderServiceReceivesCreateStandingOrder(
            userId = userId,
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-standing-order-create",
            accountId = accountId,
            payeeId = payeeId,
            amountMinorUnits = 95_000L,
            frequency = "MONTHLY",
            startDate = startDate.toString(),
            reference = "SO-RENT",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "standingOrderId" To something
                "accountId" To accountId.toString()
                "payeeId" To payeeId.toString()
                "payeeName" To "Landlord"
                "payeeSortCode" To "20-00-00"
                "payeeAccountNumber" To "12345678"
                "reference" To "SO-RENT"
                "amountMinorUnits" To 95_000
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To startDate.toString()
                "nextPaymentAt" To "${startDate}T00:00:00Z"
                noExtraProps()
            }
        }

        val standingOrderId = uuidFromLastResponse("standingOrderId")

        whenever(standingOrderServiceReceivesGetStandingOrders(
            userId = userId,
            accountHolderId = accountHolderId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To listOf(
                    json {
                        "standingOrderId" To standingOrderId.toString()
                        "accountId" To accountId.toString()
                        "payeeId" To payeeId.toString()
                        "payeeName" To "Landlord"
                        "payeeSortCode" To "20-00-00"
                        "payeeAccountNumber" To "12345678"
                        "reference" To "SO-RENT"
                        "amountMinorUnits" To 95_000
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To startDate.toString()
                        "nextPaymentAt" To "${startDate}T00:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        whenever(standingOrderServiceReceivesCancelStandingOrder(
            userId = userId,
            accountHolderId = accountHolderId,
            standingOrderId = standingOrderId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        whenever(standingOrderServiceReceivesGetStandingOrders(
            userId = userId,
            accountHolderId = accountHolderId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To listOf(
                    json {
                        "standingOrderId" To standingOrderId.toString()
                        "accountId" To accountId.toString()
                        "payeeId" To payeeId.toString()
                        "payeeName" To "Landlord"
                        "payeeSortCode" To "20-00-00"
                        "payeeAccountNumber" To "12345678"
                        "reference" To "SO-RENT"
                        "amountMinorUnits" To 95_000
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "CANCELLED"
                        "startDate" To startDate.toString()
                        "cancelledAt" To something
                        "nextPaymentAt" To "${startDate}T00:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "CUSTOMER", "ACCOUNT_HOLDER_CREATED"),
                AuditEventInDb(7, null, "CUSTOMER", "INDIVIDUAL_CREATED"),
                AuditEventInDb(8, someUserId(), "CUSTOMER", "ACCOUNT_CREATED"),
                AuditEventInDb(9, someUserId(), "CUSTOMER", "PAYEE_ADDED"),
                AuditEventInDb(10, someUserId(), "CUSTOMER", "STANDING_ORDER_CREATED"),
                AuditEventInDb(11, someUserId(), "CUSTOMER", "STANDING_ORDER_CANCELLED"),
            )
        }
    }

    @Test
    fun getStandingOrdersRejectsRequestWithoutJwt() = malaiTest {
        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/standing-orders")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
