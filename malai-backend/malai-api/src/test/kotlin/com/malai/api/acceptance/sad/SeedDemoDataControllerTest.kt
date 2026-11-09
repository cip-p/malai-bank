package com.malai.api.acceptance.sad

import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED

class SeedDemoDataControllerTest : MalaiApiAcceptanceTest() {

    @Test
    fun returns401WhenJwtTokenIsMissing() = malaiAcceptanceTest {
        whenever(malaiApiReceives(
            post()
                .url("/api/v1/seed-demo-data")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun returns404WhenJwtUserIsNotInDatabase() = malaiAcceptanceTest {
        whenever(malaiApiReceives(
            post()
                .url("/api/v1/seed-demo-data")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8979"
                noExtraProps()
            }
        }
    }
}
