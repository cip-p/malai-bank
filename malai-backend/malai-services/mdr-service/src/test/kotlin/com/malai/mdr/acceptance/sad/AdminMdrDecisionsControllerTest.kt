package com.malai.mdr.acceptance.sad

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import java.util.UUID

class AdminMdrDecisionsControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val someUserId = UUID.fromString("cc000000-0000-0000-0000-000000000001")
    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000010")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000010")
    private val unknownDecisionId = UUID.fromString("dec00000-0000-0000-0000-0000000000ff")
    private val acceptEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000001")
    private val rejectEntryId = UUID.fromString("d1000000-0000-0000-0000-000000000002")

    @Test
    fun actionableOutputWithNoDispositionRefusesWith409() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6991"
                noExtraProps()
            }
        }
    }

    @Test
    fun actionableOutputUnderARejectRulingRefusesWith409() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anMdrHumanDispositionIsRecorded(rejectEntryId, someDecisionId, decisionEntryId, "REJECT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6991"
                noExtraProps()
            }
        }
    }

    @Test
    fun latestWinsAnAcceptFollowedByARejectRefusesWith409() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(anMdrHumanDispositionIsRecorded(acceptEntryId, someDecisionId, decisionEntryId, "ACCEPT"))
        and(anMdrHumanDispositionIsRecorded(rejectEntryId, someDecisionId, decisionEntryId, "REJECT"))

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
        }
    }

    @Test
    fun actionableOutputForAnUnknownDecisionReturns404() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$unknownDecisionId/actionable-output")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
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
    fun replayForAnUnknownDecisionReturns404() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$unknownDecisionId")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
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
    fun worklistWithAnUnknownRulingStateReturns400() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("rulingState", "MAYBE")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun worklistWithAnUnparseableAdvisoryValueReturns400() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .queryParam("advisory", "maybe")
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }

    @Test
    fun customerJwtOnTheWorklistReturns403() = malaiAcceptanceTest {
        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions")
                    .withCustomerJwtTokenForUserId(someUserId),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun customerJwtOnTheReplayReadReturns403() = malaiAcceptanceTest {
        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId")
                    .withCustomerJwtTokenForUserId(someUserId),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun supportAgentJwtOnTheActionableOutputGateReturns403() = malaiAcceptanceTest {
        whenever(
            mdrServiceReceives(
                get()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/actionable-output")
                    .withJwtTokenForUserId(someUserId, listOf("support_agent")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }
}
