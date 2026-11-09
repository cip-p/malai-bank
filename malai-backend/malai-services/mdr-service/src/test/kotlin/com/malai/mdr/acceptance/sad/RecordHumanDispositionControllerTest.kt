package com.malai.mdr.acceptance.sad

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.mdr.support.builders.RecordHumanDispositionRequestBuilder.Companion.anAcceptDispositionRequest
import com.malai.mdr.support.builders.RecordHumanDispositionRequestBuilder.Companion.anEditDispositionRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT
import java.util.UUID

class RecordHumanDispositionControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val someUserId = UUID.fromString("cc000000-0000-0000-0000-000000000001")
    private val decisionEntryId = UUID.fromString("d0000000-0000-0000-0000-000000000010")
    private val someDecisionId = UUID.fromString("dec00000-0000-0000-0000-000000000010")
    private val orphanDispositionEntryId = UUID.fromString("d1000000-0000-0000-0000-0000000000a1")
    private val dispositionOnlyDecisionId = UUID.fromString("dec00000-0000-0000-0000-0000000000a2")
    private val newDispositionEntryId = UUID.fromString("d1000000-0000-0000-0000-0000000000b1")

    @Test
    fun rejectDispositionOnAnUnknownDecision() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(newDispositionEntryId))
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

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun rejectADispositionTargetingADisposition() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(decisionEntryId, someDecisionId))
        and(
            anMdrHumanDispositionIsRecorded(
                entryId = orphanDispositionEntryId,
                decisionId = dispositionOnlyDecisionId,
                parentEntryId = decisionEntryId,
                disposition = "ACCEPT",
            ),
        )

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$dispositionOnlyDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(newDispositionEntryId))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe UNPROCESSABLE_CONTENT
            body containsProp json {
                "message" To "7965"
                noExtraProps()
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 2
        }
    }

    @Test
    fun rejectAnEditDispositionWithoutItsEditedOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anEditDispositionRequest().withEntryId(newDispositionEntryId).withEditedOutput(null))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
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
    fun rejectAnAcceptDispositionCarryingAnEditedOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(newDispositionEntryId).withEditedOutput("sneaky replacement"))
                    .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
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
    fun customerJwtOnDispositionEndpointReturns403() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(newDispositionEntryId))
                    .withCustomerJwtTokenForUserId(someUserId),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun supportAgentJwtOnDispositionEndpointReturns403() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(
            mdrServiceReceives(
                post()
                    .url("/api/v1/admin/mdr/decisions/$someDecisionId/dispositions")
                    .body(anAcceptDispositionRequest().withEntryId(newDispositionEntryId))
                    .withJwtTokenForUserId(someUserId, listOf("support_agent")),
            ),
        )

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }
}
