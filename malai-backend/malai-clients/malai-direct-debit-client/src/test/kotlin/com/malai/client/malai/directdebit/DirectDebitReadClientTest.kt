package com.malai.client.malai.directdebit

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.ActiveDirectDebitBuilder.Companion.anActiveDirectDebit
import com.malai.client.malai.directdebit.builders.response.ActiveDirectDebitsResponseBuilder.Companion.anActiveDirectDebitsResponse
import com.malai.client.malai.directdebit.model.ActiveDirectDebit
import com.malai.client.malai.directdebit.model.ActiveDirectDebitsResponse
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
class DirectDebitReadClientTest : MalaiClientTest(), WithDirectDebitsApi {

    @Autowired
    private lateinit var client: DirectDebitReadClient

    private var lastResponse: ActiveDirectDebitsResponse? = null

    @Test
    fun getsActiveDirectDebitsForAccountAndDeserialisesResponse() {
        given(getActiveDirectDebitsReturns(
            accountId = someAccountUid(),
            anActiveDirectDebitsResponse().withDirectDebits(
                anActiveDirectDebit()
                    .withDirectDebitId(someMandateUid())
                    .withAccountId(someAccountUid())
                    .withCreditorName("British Gas")
                    .withAmountMinorUnits(4_500)
                    .withCurrency("GBP")
                    .withFrequency("MONTHLY")
                    .withStatus("ACTIVE")
                    .withStartDate("2026-06-01"),
            ),
        ))

        whenever(clientGetsActiveDirectDebits(someAccountUid()))

        then(theLastResponse()) {
            this shouldBe ActiveDirectDebitsResponse(
                directDebits = listOf(
                    ActiveDirectDebit(
                        directDebitId = someMandateUid(),
                        accountId = someAccountUid(),
                        creditorName = "British Gas",
                        amountMinorUnits = 4_500,
                        currency = "GBP",
                        frequency = "MONTHLY",
                        status = "ACTIVE",
                        startDate = "2026-06-01",
                    ),
                ),
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/direct-debit-service/api/v1/internal/direct-debits?accountId=${someAccountUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    private fun clientGetsActiveDirectDebits(accountId: UUID) = Action<ActionContext> {
        lastResponse = client.getActiveDirectDebits("some-service-token", accountId)
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No response captured")
    }
}
