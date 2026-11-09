package com.malai.mdr.acceptance.sad

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

class InternalDecisionRecordControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000110")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000110")
    private val unknownDecisionId = UUID.fromString("dec00000-0000-0000-0000-0000000001ff")

    @Test
    fun theDecisionRecordForAnUnknownDecisionReturns404() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$unknownDecisionId")
                    .withServiceJwtToken(),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8977"
                noExtraProps()
            }
        }
    }

    @Test
    fun theDecisionRecordWithoutAServiceJwtReturns401() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId"),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun anAdminUserJwtOnTheInternalDecisionRecordReturns401() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/internal/mdr/decisions/$someDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }
}
