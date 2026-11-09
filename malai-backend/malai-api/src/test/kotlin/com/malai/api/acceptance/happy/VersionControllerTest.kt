package com.malai.api.acceptance.happy

import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType.TEXT_PLAIN

class VersionControllerTest : MalaiApiAcceptanceTest() {

    @Test
    fun versionReturnsOkWithApplicationVersion() = malaiAcceptanceTest {
        whenever(malaiApiReceives(get().url("/version")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            headers.contentType?.isCompatibleWith(TEXT_PLAIN) shouldBe true
            body shouldNotBe null
        }
    }
}
