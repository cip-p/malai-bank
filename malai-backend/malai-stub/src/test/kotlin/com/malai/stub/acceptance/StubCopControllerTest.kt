package com.malai.stub.acceptance

import com.malai.stub.builders.request.StubCopLookupRequestBuilder.Companion.aStubCopLookupRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class StubCopControllerTest : MalaiStubAcceptanceTest() {

    @Test
    fun postReturnsMatchWhenSubmittedNameExactlyEqualsOnRecordName() = malaiAcceptanceTest {
        whenever(malaiStubReceives(
            post()
                .url("/cop/lookup")
                .header("Content-Type", "application/json")
                .body(aStubCopLookupRequest()
                    .withSortCode("60-00-01")
                    .withAccountNumber("12345678")
                    .withName("Alice Walker"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "verdict" To "MATCH"
                "onRecordName" To null
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsCloseMatchWithOnRecordNameWhenSubmittedNameIsAboveSimilarityThreshold() = malaiAcceptanceTest {
        whenever(malaiStubReceives(
            post()
                .url("/cop/lookup")
                .header("Content-Type", "application/json")
                .body(aStubCopLookupRequest()
                    .withSortCode("60-00-01")
                    .withAccountNumber("22334455")
                    .withName("Octopus Energy Co Ltd"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "verdict" To "CLOSE_MATCH"
                "onRecordName" To "Octopus Energy Ltd"
                noExtraProps()
            }
        }
    }

    @Test
    fun postReturnsNoMatchAndDoesNotLeakOnRecordNameWhenSortCodeAndAccountNumberAreUnknown() = malaiAcceptanceTest {
        whenever(malaiStubReceives(
            post()
                .url("/cop/lookup")
                .header("Content-Type", "application/json")
                .body(aStubCopLookupRequest()
                    .withSortCode("99-99-99")
                    .withAccountNumber("99999999")
                    .withName("Alice Walker"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "verdict" To "NO_MATCH"
                "onRecordName" To null
                noExtraProps()
            }
        }
    }
}
