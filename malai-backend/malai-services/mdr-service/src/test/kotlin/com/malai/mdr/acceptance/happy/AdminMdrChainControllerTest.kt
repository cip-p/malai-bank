package com.malai.mdr.acceptance.happy

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class AdminMdrChainControllerTest : MdrAcceptanceTest() {

    private val someAdminUserId = UUID.fromString("ad000000-0000-0000-0000-000000000001")
    private val firstEntryId = UUID.fromString("e0000000-0000-0000-0000-000000000001")
    private val firstDecisionId = UUID.fromString("d0000000-0000-0000-0000-000000000001")
    private val secondEntryId = UUID.fromString("e0000000-0000-0000-0000-000000000002")
    private val secondDecisionId = UUID.fromString("d0000000-0000-0000-0000-000000000002")
    private val dispositionEntryId = UUID.fromString("e1000000-0000-0000-0000-000000000001")

    @Test
    fun adminVerifiesTheMdrChainOnAGenesisChain() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 0
                noExtraProps()
            }
        }
    }

    @Test
    fun adminVerifiesTheMdrChainWithRowsVerifiedForAnIntactChain() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(firstEntryId, firstDecisionId))
        and(anMdrModelDecisionIsRecorded(secondEntryId, secondDecisionId))

        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 2
                noExtraProps()
            }
        }
    }

    @Test
    fun adminVerifiesAChainMixingAdvisoryAndNonAdvisoryDecisionsWithARuling() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(firstEntryId, firstDecisionId))
        and(anMdrModelDecisionIsRecorded(secondEntryId, secondDecisionId, advisory = true))
        and(anMdrHumanDispositionIsRecorded(dispositionEntryId, secondDecisionId, secondEntryId, "ACCEPT"))

        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 3
                noExtraProps()
            }
        }
    }

    @Test
    fun tamperingTheAdvisoryFlagMakesVerificationReportTheMismatchNamingThatSeq() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(firstEntryId, firstDecisionId))
        and(anMdrModelDecisionIsRecorded(secondEntryId, secondDecisionId, advisory = true))
        and(theAdvisoryFlagOfMdrEntryAtSeqIsTamperedTo(seq = 2L, advisory = false))

        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "CURRENT_HASH_MISMATCH"
                "atSeq" To 2
                noExtraProps()
            }
        }
    }

    @Test
    fun tamperingAStoredHashMakesVerificationReportTheMismatchNamingThatSeq() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anMdrModelDecisionIsRecorded(firstEntryId, firstDecisionId))
        and(anMdrModelDecisionIsRecorded(secondEntryId, secondDecisionId))
        and(theCurrentHashOfMdrEntryAtSeqIsTamperedTo(seq = 2L, badHash = ByteArray(32) { 0xFF.toByte() }))

        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someAdminUserId, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "CURRENT_HASH_MISMATCH"
                "atSeq" To 2
                noExtraProps()
            }
        }
    }
}
