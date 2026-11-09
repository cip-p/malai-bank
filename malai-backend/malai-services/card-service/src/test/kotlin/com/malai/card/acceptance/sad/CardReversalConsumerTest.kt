package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardAuthorisationStatus.REVERSED
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardReversalConsumerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000f2")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000f2")

    private fun aStubbedReleaseHold() = releaseHoldReturns(aHoldResponse()
        .withHoldId(authorisationId)
        .withStatus("RELEASED")
        .withResolvedAt("2026-10-15T10:30:00Z"))

    @Test
    fun reversalOfAnAlreadySettledAuthorisationIsIgnoredAndReleasesNoHold() = malaiAcceptanceTest {
        val settledSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000fa")
        val settledAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000fa")
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aStubbedReleaseHold())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = settledAuthorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = settledSchemeReference,
            amountMinorUnits = 4_370,
            status = SETTLED,
        ))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemeReverses(settledSchemeReference, groupId = "reversal-barrier"))
        and(theSchemeReverses(schemeReference, groupId = "reversal-barrier"))

        then(theAuthorisationOnceReversed(authorisationId)) {
            this.status shouldBe REVERSED
        }

        and(theAuthorisationInTheDatabase(settledAuthorisationId)) {
            this.status shouldBe SETTLED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/release")
        }) {
            this shouldHaveSize 1
            this.single().url shouldBe "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/release"
        }
    }

    @Test
    fun reversalForAnUnknownSchemeReferenceIsIgnoredWithoutBlockingTheQueue() = malaiAcceptanceTest {
        val unknownSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000fb")
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aStubbedReleaseHold())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemeReverses(unknownSchemeReference, groupId = "reversal-barrier"))
        and(theSchemeReverses(schemeReference, groupId = "reversal-barrier"))

        then(theAuthorisationOnceReversed(authorisationId)) {
            this.status shouldBe REVERSED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/release")
        }) {
            this shouldHaveSize 1
            this.single().url shouldBe "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/release"
        }
    }
}
