package com.malai.client.malai.card.metrics

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.card.builders.response.CardsIssuedResponseBuilder.Companion.aCardsIssuedResponse
import com.malai.client.malai.card.model.CardsIssuedResponse
import com.malai.test.acceptance.MalaiClientTest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import java.time.Instant

@MalaiClientSpringTest
class CardMetricsClientTest : MalaiClientTest(), WithCardMetricsApi {

    @Autowired
    private lateinit var cardMetricsClient: CardMetricsClient

    private var lastResponse: CardsIssuedResponse? = null
    private var lastThrowable: Throwable? = null

    private val windowStart = Instant.parse("2026-07-19T23:00:00Z")
    private val windowEnd = Instant.parse("2026-07-20T23:00:00Z")

    @Test
    fun readsTheCountOfCardsIssuedInAWindow() {
        given(cardsIssuedReturns(aCardsIssuedResponse().withCount(9)))

        whenever(clientCallsCardsIssued("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe CardsIssuedResponse(count = 9)
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/card-service/api/v1/internal/cards/issued-count")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            url shouldBe "/card-service/api/v1/internal/cards/issued-count" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun aQuietWindowDeserialisesAsZeroRatherThanAnAbsentCount() {
        given(cardsIssuedReturns(aCardsIssuedResponse().withCount(0)))

        whenever(clientCallsCardsIssued("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe CardsIssuedResponse(count = 0)
        }
    }

    @Test
    fun surfacesAMetricReadFailureAsAClientResponseException() {
        given(cardsIssuedFailsWithServerError())

        whenever(clientAttemptsToReadCardsIssued("some-service-token", windowStart, windowEnd))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun clientCallsCardsIssued(serviceToken: String, from: Instant, until: Instant) = Action<ActionContext> {
        lastResponse = cardMetricsClient.cardsIssued(serviceToken, from, until)
    }

    private fun clientAttemptsToReadCardsIssued(serviceToken: String, from: Instant, until: Instant) = Action<ActionContext> {
        lastThrowable = runCatching {
            cardMetricsClient.cardsIssued(serviceToken, from, until)
        }.exceptionOrNull()
    }

    private fun theLastResponse() = StateCollector { _ -> lastResponse ?: error("No cards-issued response captured") }

    private fun theLastThrowable() = StateCollector { _ -> lastThrowable ?: error("No throwable captured") }
}
