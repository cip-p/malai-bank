package com.malai.api.acceptance.happy

import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class ReadyControllerTest : MalaiApiAcceptanceTest() {

    @Test
    fun readyReturnsOkWithEmptyBody() = malaiAcceptanceTest {
        whenever(malaiApiReceives(get().url("/ready")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body shouldBe null
        }
    }
}
