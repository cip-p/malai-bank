package com.malai.client.malai.stub.cop

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.stub.cop.builders.response.StubCopLookupResponseBuilder.Companion.aStubCopLookupResponse
import com.malai.client.malai.stub.cop.model.StubCopLookupRequest
import com.malai.client.malai.stub.cop.model.StubCopLookupResponse
import com.malai.client.malai.stub.cop.model.StubCopVerdict
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.POST

@MalaiClientSpringTest
class MalaiStubCopClientTest : MalaiClientTest(), WithMalaiStub {

    @Autowired
    private lateinit var client: MalaiStubCopClient

    private var lastResponse: StubCopLookupResponse? = null

    @Test
    fun matchVerdict() {
        given(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("MATCH")))

        whenever(clientCallsLookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "12345678",
            name = "Alice Walker",
        )))

        then(theLastResponse()) {
            this shouldBe StubCopLookupResponse(verdict = StubCopVerdict.MATCH)
        }
        and(madeTheFollowingHttpRequest {
            url == "/malai-stub/cop/lookup"
        }) {
            method shouldBe POST
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "sortCode" To "60-00-01"
                "accountNumber" To "12345678"
                "name" To "Alice Walker"
                noExtraProps()
            }
        }
    }

    @Test
    fun closeMatchVerdictIncludesOnRecordName() {
        given(stubCopLookupReturns(
            aStubCopLookupResponse()
                .withVerdict("CLOSE_MATCH")
                .withOnRecordName("Octopus Energy Ltd")
        ))

        whenever(clientCallsLookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "12345678",
            name = "Octopus Energy Co Ltd",
        )))

        then(theLastResponse()) {
            this shouldBe StubCopLookupResponse(
                verdict = StubCopVerdict.CLOSE_MATCH,
                onRecordName = "Octopus Energy Ltd",
            )
        }
    }

    @Test
    fun noMatchVerdict() {
        given(stubCopLookupReturns(aStubCopLookupResponse().withVerdict("NO_MATCH")))

        whenever(clientCallsLookup(StubCopLookupRequest(
            sortCode = "60-00-01",
            accountNumber = "99999999",
            name = "Unknown",
        )))

        then(theLastResponse()) {
            this shouldBe StubCopLookupResponse(verdict = StubCopVerdict.NO_MATCH)
        }
    }

    private fun clientCallsLookup(request: StubCopLookupRequest) = Action<ActionContext> {
        lastResponse = client.lookup(request)
    }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No lookup response captured")
    }
}
