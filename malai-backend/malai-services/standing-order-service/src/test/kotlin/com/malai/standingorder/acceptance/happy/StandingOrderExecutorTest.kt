package com.malai.standingorder.acceptance.happy

import com.malai.client.malai.payments.builders.response.InitiatePaymentResponseBuilder.Companion.anInitiatePaymentResponse
import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.commons.schedule.ScheduledPaymentEndToEndId
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.standingorder.scheduler.StandingOrderExecutor
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class StandingOrderExecutorTest : StandingOrdersAcceptanceTest() {

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private val executor get() = getBean(StandingOrderExecutor::class.java)

    private fun theStandingOrderExecutorRuns() = Action<ActionContext> { executor.executeDueOrders() }

    private fun aScheduledPaymentAccepted() = anInitiatePaymentResponse()
        .withPaymentId(somePaymentUid())
        .withState("PENDING")
        .withRail("FPS")
        .withDirection("OUTBOUND")
        .withCreatedAt(Instant.parse("2026-06-01T09:00:00Z"))
        .withEndToEndId(UUID.randomUUID())
        .withCurrency("GBP")
        .withAmountMinorUnits(95000)

    private fun expectedEndToEndIdFor(occurrenceDate: String): UUID =
        ScheduledPaymentEndToEndId.forOccurrence(someMandateUid(), LocalDate.parse(occurrenceDate))

    @Test
    fun dueActiveOrderInitiatesAScheduledPaymentWithADeterministicEndToEndIdAndAdvancesToTheNextOccurrence() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(initiateScheduledPaymentReturns(aScheduledPaymentAccepted()))
            and(anAccountAndStandingOrderExist(
                accountHolderId = someAccountHolderUid(),
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                payeeName = "Landlord",
                payeeSortCode = "20-00-00",
                payeeAccountNumber = "12345678",
                reference = "SO-RENT",
                amountMinorUnits = 95000,
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-06-01"),
                nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            ))
            and(nowIs("2026-06-01T09:00:00Z"))

            whenever(theStandingOrderExecutorRuns())

            then(standingOrderServiceMadeTheFollowingHttpRequest {
                url == "/payments-service/api/v1/internal/payments"
            }) {
                method shouldBe POST
                body containsProp json {
                    "endToEndId" To expectedEndToEndIdFor("2026-06-01").toString()
                    "fromAccountId" To someAccountUid().toString()
                    "accountHolderId" To someAccountHolderUid().toString()
                    "toSortCode" To "20-00-00"
                    "toAccountNumber" To "12345678"
                    "toName" To "Landlord"
                    "amountMinorUnits" To 95000
                    "currency" To "GBP"
                    "reference" To "SO-RENT"
                    noExtraProps()
                }
            }

            and(theStandingOrderInTheDatabase(someMandateUid())) {
                this shouldBe StandingOrderEntity(
                    standingOrderId = someMandateUid(),
                    accountId = someAccountUid(),
                    payeeId = null,
                    payeeName = "Landlord",
                    payeeSortCode = "20-00-00",
                    payeeAccountNumber = "12345678",
                    reference = "SO-RENT",
                    amountMinorUnits = 95000,
                    currency = Currency.GBP,
                    frequency = Frequency.MONTHLY,
                    status = InstrumentStatus.ACTIVE,
                    startDate = LocalDate.parse("2026-06-01"),
                    cancelledAt = null,
                    nextPaymentAt = Instant.parse("2026-07-01T00:00:00Z"),
                    createdAt = Instant.parse("2026-04-01T09:00:00Z"),
                )
            }
        }

    @Test
    fun anOrderOverdueBySeveralPeriodsFiresExactlyOnceAndResynchronisesToTheFirstFutureOccurrence() =
        malaiAcceptanceTest {
            given(oauth2ServiceTokenEndpointReturnsToken())
            and(initiateScheduledPaymentReturns(aScheduledPaymentAccepted()))
            and(anAccountAndStandingOrderExist(
                accountHolderId = someAccountHolderUid(),
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-01-01"),
                nextPaymentAt = Instant.parse("2026-01-01T00:00:00Z"),
            ))
            and(nowIs("2026-07-20T09:00:00Z"))

            whenever(theStandingOrderExecutorRuns())

            then(standingOrderServiceMadeTheFollowingHttpRequests {
                url == "/payments-service/api/v1/internal/payments"
            }) {
                this shouldHaveSize 1
                this[0].body containsProp json {
                    "endToEndId" To expectedEndToEndIdFor("2026-01-01").toString()
                    "fromAccountId" To someAccountUid().toString()
                    "accountHolderId" To someAccountHolderUid().toString()
                    "toSortCode" To "20-00-00"
                    "toAccountNumber" To "12345678"
                    "toName" To "Landlord"
                    "amountMinorUnits" To 95000
                    "currency" To "GBP"
                    noExtraProps()
                }
            }

            and(theStandingOrderInTheDatabase(someMandateUid())) {
                this!!.nextPaymentAt shouldBe Instant.parse("2026-08-01T00:00:00Z")
                this.status shouldBe InstrumentStatus.ACTIVE
            }
        }

    @Test
    fun aMonthEndAnchoredOrderPaysTheLastDayOfAShortMonthAndReturnsToThe31st() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(initiateScheduledPaymentReturns(aScheduledPaymentAccepted()))
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-01-31"),
            nextPaymentAt = Instant.parse("2026-02-28T00:00:00Z"),
        ))
        and(nowIs("2026-02-28T09:00:00Z"))

        whenever(theStandingOrderExecutorRuns())

        then(standingOrderServiceMadeTheFollowingHttpRequest {
            url == "/payments-service/api/v1/internal/payments"
        }) {
            body containsProp json {
                "endToEndId" To expectedEndToEndIdFor("2026-02-28").toString()
                "fromAccountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "toSortCode" To "20-00-00"
                "toAccountNumber" To "12345678"
                "toName" To "Landlord"
                "amountMinorUnits" To 95000
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this!!.nextPaymentAt shouldBe Instant.parse("2026-03-31T00:00:00Z")
        }
    }

    @Test
    fun aReFiredOccurrenceCarriesTheSameDeterministicEndToEndIdSoItBouncesHarmlessly() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(initiateScheduledPaymentReturns(aScheduledPaymentAccepted()))
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-06-01"),
            nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))
        and(nowIs("2026-06-01T09:00:00Z"))

        whenever(theStandingOrderExecutorRuns())
        and(theStandingOrderNextPaymentIsResetTo(someMandateUid(), Instant.parse("2026-06-01T00:00:00Z")))
        whenever(theStandingOrderExecutorRuns())

        then(standingOrderServiceMadeTheFollowingHttpRequests {
            url == "/payments-service/api/v1/internal/payments"
        }) {
            this shouldHaveSize 2
            this.forEach { request ->
                request.body containsProp json {
                    "endToEndId" To expectedEndToEndIdFor("2026-06-01").toString()
                    "fromAccountId" To someAccountUid().toString()
                    "accountHolderId" To someAccountHolderUid().toString()
                    "toSortCode" To "20-00-00"
                    "toAccountNumber" To "12345678"
                    "toName" To "Landlord"
                    "amountMinorUnits" To 95000
                    "currency" To "GBP"
                    noExtraProps()
                }
            }
        }
    }
}
