package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardPresentmentConsumerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000e1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e1")

    private fun aStubbedSettleHold() = settleHoldReturns(aSettleHoldResponse()
        .withHoldId(authorisationId)
        .withTransaction(aPostTransactionResponse()
            .withTransactionId(UUID.randomUUID())
            .withPostedAt("2026-10-15T10:30:00Z")))

    @Test
    fun presentmentExceedingTheAuthorisedAmountIsRefusedAndPlacesNoLedgerSettlement() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aStubbedSettleHold())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 5_000))
        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370))

        then(theAuthorisationOnceSettled(authorisationId)) {
            this.status shouldBe SETTLED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/settle"
        }) {
            this shouldHaveSize 1
            this.single().body containsProp json {
                "amountMinorUnits" To 4_370
            }
        }
    }

    @Test
    fun presentmentForAnUnknownSchemeReferenceIsIgnoredWithoutBlockingTheQueue() = malaiAcceptanceTest {
        val unknownSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e9")
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aStubbedSettleHold())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemePresents(unknownSchemeReference, presentedAmountMinorUnits = 999, groupId = "presentment-barrier"))
        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370, groupId = "presentment-barrier"))

        then(theAuthorisationOnceSettled(authorisationId)) {
            this.status shouldBe SETTLED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/settle")
        }) {
            this shouldHaveSize 1
            this.single().url shouldBe "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/settle"
        }
    }

    @Test
    fun presentmentForAnAlreadyDeclinedAuthorisationIsIgnored() = malaiAcceptanceTest {
        val declinedSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000ea")
        val declinedAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000ea")
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(aStubbedSettleHold())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = declinedAuthorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = declinedSchemeReference,
            amountMinorUnits = 4_370,
            status = DECLINED,
            declineReason = CARD_FROZEN,
            holdId = null,
        ))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemePresents(declinedSchemeReference, presentedAmountMinorUnits = 4_370, groupId = "presentment-barrier"))
        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370, groupId = "presentment-barrier"))

        then(theAuthorisationOnceSettled(authorisationId)) {
            this.status shouldBe SETTLED
        }

        and(theAuthorisationInTheDatabase(declinedAuthorisationId)) {
            this.status shouldBe DECLINED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/settle")
        }) {
            this shouldHaveSize 1
        }
    }
}
