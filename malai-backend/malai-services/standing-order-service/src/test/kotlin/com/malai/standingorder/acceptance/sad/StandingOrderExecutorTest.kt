package com.malai.standingorder.acceptance.sad

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.standingorder.entity.StandingOrderEntity
import com.malai.standingorder.scheduler.StandingOrderExecutor
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class StandingOrderExecutorTest : StandingOrdersAcceptanceTest() {

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private val executor get() = getBean(StandingOrderExecutor::class.java)

    private fun theStandingOrderExecutorRuns() = Action<ActionContext> { executor.executeDueOrders() }

    @Test
    fun insufficientFundsRecordsAMissedOccurrenceAdvancesTheScheduleAndLeavesTheOrderActiveWithoutRetrying() =
        malaiAcceptanceTest {
            given(theAuditChainIsAtGenesis())
            and(oauth2ServiceTokenEndpointReturnsToken())
            and(initiateScheduledPaymentReturnsErrorStatus(400, """{"message":"7983"}"""))
            and(anAccountAndStandingOrderExist(
                accountHolderId = someAccountHolderUid(),
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                reference = "SO-RENT",
                amountMinorUnits = 95000,
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-06-01"),
                nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            ))
            and(nowIs("2026-06-01T09:00:00Z"))

            whenever(theStandingOrderExecutorRuns())

            then(standingOrderServiceMadeTheFollowingHttpRequests {
                url == "/payments-service/api/v1/internal/payments"
            }) {
                this shouldHaveSize 1
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

            and(theAuditEventsInTheDatabase()) {
                this shouldHaveSize 1
                this[0]["action"] shouldBe "STANDING_ORDER_PAYMENT_MISSED"
                this[0]["actor_type"] shouldBe "SYSTEM"
                this[0]["actor_id"].shouldBeNull()
                this[0]["resource_type"] shouldBe "StandingOrder"
                this[0]["resource_id"] shouldBe someMandateUid().toString()
            }
        }

    @Test
    fun cancelledOrdersAreNeverScannedEvenWhenTheirNextPaymentDateHasPassed() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = someAccountUid(),
            status = InstrumentStatus.CANCELLED,
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-06-01"),
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
            nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-06-01T09:00:00Z"))

        whenever(theStandingOrderExecutorRuns())

        then(standingOrderServiceMadeTheFollowingHttpRequests {
            url == "/payments-service/api/v1/internal/payments"
        }) {
            this shouldHaveSize 0
        }

        and(theStandingOrderInTheDatabase(someMandateUid())) {
            this shouldBe StandingOrderEntity(
                standingOrderId = someMandateUid(),
                accountId = someAccountUid(),
                payeeId = null,
                payeeName = "Landlord",
                payeeSortCode = "20-00-00",
                payeeAccountNumber = "12345678",
                reference = null,
                amountMinorUnits = 95000,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
                nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }
    }
}
