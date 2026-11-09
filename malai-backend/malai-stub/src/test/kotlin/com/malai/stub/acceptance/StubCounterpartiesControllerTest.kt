package com.malai.stub.acceptance

import com.malai.stub.directory.StubFictionalDirectory
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.Prop
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class StubCounterpartiesControllerTest : MalaiStubAcceptanceTest() {

    @Test
    fun getReturnsEveryConfiguredFixtureInTheCanonicalWireShape() = malaiAcceptanceTest {
        whenever(malaiStubReceives(
            get()
                .url("/scenarios/counterparties")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "counterparties" To expectedCounterparties()
                noExtraProps()
            }
        }
    }

    private fun expectedCounterparties(): List<Prop> =
        getBean(StubFictionalDirectory::class.java).all().map { account ->
            json {
                "sortCode" To account.sortCode
                "accountNumber" To account.accountNumber
                "bank" To account.bank
                "nameOnRecord" To account.name
                "outboundOutcome" To account.outboundOutcome.name
                if (account.returnReason != null) {
                    "returnReason" To account.returnReason.name
                }
                noExtraProps()
            }
        }
}
