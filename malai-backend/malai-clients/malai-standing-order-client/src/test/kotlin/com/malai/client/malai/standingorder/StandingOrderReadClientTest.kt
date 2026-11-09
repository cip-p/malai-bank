package com.malai.client.malai.standingorder

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.ActiveStandingOrderBuilder.Companion.anActiveStandingOrder
import com.malai.client.malai.standingorder.builders.response.ActiveStandingOrdersResponseBuilder.Companion.anActiveStandingOrdersResponse
import com.malai.client.malai.standingorder.model.ActiveStandingOrder
import com.malai.client.malai.standingorder.model.ActiveStandingOrdersResponse
import com.malai.test.acceptance.MalaiClientTest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import java.util.UUID

@MalaiClientSpringTest
class StandingOrderReadClientTest : MalaiClientTest(), WithStandingOrdersApi {

    @Autowired
    private lateinit var client: StandingOrderReadClient

    private var lastResponse: ActiveStandingOrdersResponse? = null

    @Test
    fun getsActiveStandingOrdersForAccountAndDeserialisesResponse() {
        given(getActiveStandingOrdersReturns(
            accountId = someAccountUid(),
            anActiveStandingOrdersResponse().withStandingOrders(
                anActiveStandingOrder()
                    .withStandingOrderId(someMandateUid())
                    .withAccountId(someAccountUid())
                    .withPayeeName("Landlord")
                    .withAmountMinorUnits(95_000)
                    .withCurrency("GBP")
                    .withFrequency("MONTHLY")
                    .withStatus("ACTIVE")
                    .withStartDate("2026-06-01"),
            ),
        ))

        whenever(clientGetsActiveStandingOrders(someAccountUid()))

        then(theLastResponse()) {
            this shouldBe ActiveStandingOrdersResponse(
                standingOrders = listOf(
                    ActiveStandingOrder(
                        standingOrderId = someMandateUid(),
                        accountId = someAccountUid(),
                        payeeName = "Landlord",
                        amountMinorUnits = 95_000,
                        currency = "GBP",
                        frequency = "MONTHLY",
                        status = "ACTIVE",
                        startDate = "2026-06-01",
                    ),
                ),
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/standing-order-service/api/v1/internal/standing-orders?accountId=${someAccountUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    private fun clientGetsActiveStandingOrders(accountId: UUID) = Action<ActionContext> {
        lastResponse = client.getActiveStandingOrders("some-service-token", accountId)
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No response captured")
    }
}
