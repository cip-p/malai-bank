package com.malai.standingorder.acceptance.happy

import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.LocalDate
import java.util.UUID

class InternalStandingOrdersControllerTest : StandingOrdersAcceptanceTest() {

    private val accountId = UUID.fromString("00000000-0000-4000-8000-0000000000d4")

    @BeforeEach
    fun resetDatabase() = cleanTables()

    @Test
    fun returnsTheAccountsActiveStandingOrders() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = accountId,
            payeeName = "Landlord",
            amountMinorUnits = 95_000,
            frequency = Frequency.MONTHLY,
            status = InstrumentStatus.ACTIVE,
            startDate = LocalDate.parse("2026-06-01"),
        ))

        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/internal/standing-orders")
                .queryParam("accountId", accountId)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To listOf(
                    json {
                        "standingOrderId" To someMandateUid().toString()
                        "accountId" To accountId.toString()
                        "payeeName" To "Landlord"
                        "amountMinorUnits" To 95_000
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To "2026-06-01"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun excludesCancelledStandingOrders() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = accountId,
            status = InstrumentStatus.CANCELLED,
            cancelledAt = java.time.Instant.parse("2026-05-20T09:00:00Z"),
        ))

        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/internal/standing-orders")
                .queryParam("accountId", accountId)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "standingOrders" To emptyList<Any>()
                noExtraProps()
            }
        }
    }
}
