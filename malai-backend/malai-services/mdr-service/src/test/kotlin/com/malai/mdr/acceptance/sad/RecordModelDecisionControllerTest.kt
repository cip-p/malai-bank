package com.malai.mdr.acceptance.sad

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.aDefaultRecordModelDecisionRequest
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.aTimedOutRecordModelDecisionRequest
import com.malai.client.malai.mdr.builders.request.RecordModelDecisionRequestBuilder.Companion.anErroredRecordModelDecisionRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.UNAUTHORIZED

class RecordModelDecisionControllerTest : MdrAcceptanceTest() {

    @Test
    fun rejectRecordDecisionWithoutServiceJwt() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(aDefaultRecordModelDecisionRequest()),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun rejectCompletedModelDecisionWithoutRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(aDefaultRecordModelDecisionRequest().withRawOutput(null))
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun rejectErroredModelDecisionWithoutErrorDetail() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(anErroredRecordModelDecisionRequest().withErrorDetail(null))
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun rejectTimedOutModelDecisionCarryingRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/internal/mdr/decisions")
                    .body(aTimedOutRecordModelDecisionRequest().withRawOutput("a late answer"))
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
            body containsProp json {
                "message" To "9999"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }
}
