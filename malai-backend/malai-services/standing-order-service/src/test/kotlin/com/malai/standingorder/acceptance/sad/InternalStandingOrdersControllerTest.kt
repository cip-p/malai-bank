package com.malai.standingorder.acceptance.sad

import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

class InternalStandingOrdersControllerTest : StandingOrdersAcceptanceTest() {

    @Test
    fun getActiveStandingOrdersWithoutServiceTokenReturns401() = malaiAcceptanceTest {
        whenever(standingOrderServiceReceives(
            get()
                .url("/api/v1/internal/standing-orders")
                .queryParam("accountId", UUID.fromString("00000000-0000-4000-8000-0000000000d4"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
