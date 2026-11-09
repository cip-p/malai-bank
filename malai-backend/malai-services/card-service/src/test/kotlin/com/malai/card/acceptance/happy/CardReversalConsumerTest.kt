package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.CardAuthorisationStatus.REVERSED
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.commons.model.Currency
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.util.UUID

class CardReversalConsumerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000f1")

    @Test
    fun reversalReleasesTheHoldMovesTheAuthorisationToReversedAndPlacesNoPosting() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(releaseHoldReturns(aHoldResponse()
            .withHoldId(authorisationId)
            .withStatus("RELEASED")
            .withResolvedAt("2026-10-15T10:30:00Z")))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemeReverses(schemeReference))

        then(theAuthorisationOnceReversed(authorisationId)) {
            this shouldBe CardAuthorisationEntity(
                authorisationId = authorisationId,
                cardId = someCardUid(),
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                schemeReference = schemeReference,
                merchantName = "Fictional Coffee Co",
                mcc = "5814",
                amountMinorUnits = 4_370,
                currency = Currency.GBP,
                status = REVERSED,
                declineReason = null,
                holdId = authorisationId,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/release"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
            body containsProp json {
                "resolvedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/api/v1/internal/ledger/transactions")
        }) {
            this shouldHaveSize 0
        }

        and(theInFlightAuthorisationsForAccount(someAccountUid())) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun redeliveryOfTheSameReversalReleasesTheHoldOnlyOnce() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(releaseHoldReturns(aHoldResponse()
            .withHoldId(authorisationId)
            .withStatus("RELEASED")
            .withResolvedAt("2026-10-15T10:30:00Z")))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemeReverses(schemeReference))
        and(theSchemeReverses(schemeReference))

        then(theAuthorisationOnceReversed(authorisationId)) {
            this.status shouldBe REVERSED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/release"
        }) {
            this shouldHaveSize 1
        }
    }
}
