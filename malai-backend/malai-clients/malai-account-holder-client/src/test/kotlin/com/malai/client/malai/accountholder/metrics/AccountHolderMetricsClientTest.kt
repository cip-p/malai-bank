package com.malai.client.malai.accountholder.metrics

import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.accountholder.builders.response.AccountHoldersCreatedResponseBuilder.Companion.anAccountHoldersCreatedResponse
import com.malai.client.malai.accountholder.model.AccountHoldersCreatedResponse
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
class AccountHolderMetricsClientTest : MalaiClientTest(), WithAccountHolderMetricsApi {

    @Autowired
    private lateinit var accountHolderMetricsClient: AccountHolderMetricsClient

    private var lastResponse: AccountHoldersCreatedResponse? = null
    private var lastThrowable: Throwable? = null

    private val windowStart = Instant.parse("2026-07-19T23:00:00Z")
    private val windowEnd = Instant.parse("2026-07-20T23:00:00Z")

    @Test
    fun readsTheCountOfAccountHoldersCreatedInAWindow() {
        given(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(12)))

        whenever(clientCallsAccountHoldersCreated("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe AccountHoldersCreatedResponse(count = 12)
        }
        and(madeTheFollowingHttpRequest {
            url.startsWith("/account-holder-service/api/v1/internal/account-holders/created-count")
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            url shouldBe "/account-holder-service/api/v1/internal/account-holders/created-count" +
                "?from=2026-07-19T23%3A00%3A00Z&until=2026-07-20T23%3A00%3A00Z"
        }
    }

    @Test
    fun aQuietWindowDeserialisesAsZeroRatherThanAnAbsentCount() {
        given(accountHoldersCreatedReturns(anAccountHoldersCreatedResponse().withCount(0)))

        whenever(clientCallsAccountHoldersCreated("some-service-token", windowStart, windowEnd))

        then(theLastResponse()) {
            this shouldBe AccountHoldersCreatedResponse(count = 0)
        }
    }

    @Test
    fun surfacesAMetricReadFailureAsAClientResponseException() {
        given(accountHoldersCreatedFailsWithServerError())

        whenever(clientAttemptsToReadAccountHoldersCreated("some-service-token", windowStart, windowEnd))

        then(theLastThrowable()) {
            this.shouldBeInstanceOf<MalaiClientResponseException>()
        }
    }

    private fun clientCallsAccountHoldersCreated(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastResponse = accountHolderMetricsClient.accountHoldersCreated(serviceToken, from, until)
    }

    private fun clientAttemptsToReadAccountHoldersCreated(
        serviceToken: String,
        from: Instant,
        until: Instant,
    ) = Action<ActionContext> {
        lastThrowable = runCatching {
            accountHolderMetricsClient.accountHoldersCreated(serviceToken, from, until)
        }.exceptionOrNull()
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No account-holders-created response captured")
    }

    private fun theLastThrowable() = StateCollector { _ ->
        lastThrowable ?: error("No throwable captured")
    }
}
