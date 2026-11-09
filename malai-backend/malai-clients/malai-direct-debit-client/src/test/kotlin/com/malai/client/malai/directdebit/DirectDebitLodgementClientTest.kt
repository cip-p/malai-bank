package com.malai.client.malai.directdebit

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.directdebit.model.LodgeMandateRequest
import com.malai.client.malai.directdebit.model.LodgedMandateResponse
import com.malai.test.acceptance.MalaiClientTest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.POST

@MalaiClientSpringTest
class DirectDebitLodgementClientTest : MalaiClientTest(), WithDirectDebitLodgement {

    @Autowired
    private lateinit var client: DirectDebitLodgementClient

    private var lastResponse: LodgedMandateResponse? = null

    @Test
    fun lodgesMandateWithExpectedBodyAndDeserialisesResponse() {
        given(lodgeMandateReturns(
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "British Gas",
            creditorSortCode = "20-00-00",
            creditorAccountNumber = "12345678",
            reference = "DD-GAS",
            amountMinorUnits = 4500,
            currency = "GBP",
            frequency = "MONTHLY",
            status = "ACTIVE",
            startDate = "2026-06-01",
            nextCollectionAt = "2026-06-01T08:00:00Z",
        ))

        whenever(clientLodges(someRequest()))

        then(theLastResponse()) {
            this shouldBe LodgedMandateResponse(
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                creditorName = "British Gas",
                creditorSortCode = "20-00-00",
                creditorAccountNumber = "12345678",
                reference = "DD-GAS",
                amountMinorUnits = 4500,
                currency = "GBP",
                frequency = "MONTHLY",
                status = "ACTIVE",
                startDate = "2026-06-01",
                cancelledAt = null,
                nextCollectionAt = "2026-06-01T08:00:00Z",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/direct-debit-service/api/v1/internal/direct-debits"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe expectedRequestBody()
        }
    }

    private fun someRequest() = LodgeMandateRequest(
        accountId = someAccountUid(),
        creditorName = "British Gas",
        creditorSortCode = "20-00-00",
        creditorAccountNumber = "12345678",
        reference = "DD-GAS",
        amountMinorUnits = 4500,
        currency = "GBP",
        frequency = "MONTHLY",
        startDate = "2026-06-01",
    )

    private fun expectedRequestBody() =
        """{"accountId":"${someAccountUid()}","creditorName":"British Gas","creditorSortCode":"20-00-00","creditorAccountNumber":"12345678","reference":"DD-GAS","amountMinorUnits":4500,"currency":"GBP","frequency":"MONTHLY","startDate":"2026-06-01"}"""

    private fun clientLodges(request: LodgeMandateRequest) = Action<ActionContext> {
        lastResponse = client.lodge("some-service-token", request)
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No lodge response captured")
    }
}
