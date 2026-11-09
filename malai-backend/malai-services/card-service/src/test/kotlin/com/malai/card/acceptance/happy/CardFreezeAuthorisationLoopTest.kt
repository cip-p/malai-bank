package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.CardVariant.DEBIT
import com.malai.card.model.DeclineReason.CARD_CANCELLED
import com.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class CardFreezeAuthorisationLoopTest : CardAcceptanceTest() {

    private val cardPan = "9904320000012341"
    private val replacementPan = "9904329999995599"

    private val replacementCardId = UUID.fromString("00000000-0000-4000-7000-0000000000c3")
    private val declinedSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c4")
    private val approvedSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c5")

    @Test
    fun freezeMakesAuthorisationsDeclineWithTheFrozenReasonAndUnfreezeRestoresApproval() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = cardPan,
            variant = DEBIT,
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse()
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(4_370)))
        and(nowIs("2026-06-15T10:30:00Z"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/freeze")
                .header("Idempotency-Key", "loop-freeze-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cardId" To someCardUid().toString()
                "accountId" To someAccountUid().toString()
                "maskedPan" To "************2341"
                "variant" To "DEBIT"
                "status" To "FROZEN"
                "expiry" To "10/29"
                noExtraProps()
            }
        }

        whenever(cardServiceReceives(anAuthorisationRequestFor(cardPan, declinedSchemeReference)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To something
                "decision" To "DECLINED"
                "declineReason" To "CARD_FROZEN"
                noExtraProps()
            }
        }
        and(theAuthorisationForSchemeReference(declinedSchemeReference)) {
            status shouldBe DECLINED
            declineReason shouldBe CARD_FROZEN
            holdId shouldBe null
            cardId shouldBe someCardUid()
        }
        and(noLedgerHoldWasPlaced())

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/unfreeze")
                .header("Idempotency-Key", "loop-unfreeze-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cardId" To someCardUid().toString()
                "accountId" To someAccountUid().toString()
                "maskedPan" To "************2341"
                "variant" To "DEBIT"
                "status" To "ACTIVE"
                "expiry" To "10/29"
                noExtraProps()
            }
        }

        whenever(cardServiceReceives(anAuthorisationRequestFor(cardPan, approvedSchemeReference)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To something
                "decision" To "APPROVED"
                noExtraProps()
            }
        }
        and(theAuthorisationForSchemeReference(approvedSchemeReference)) {
            status shouldBe AUTHORISED
            declineReason shouldBe null
            holdId shouldBe authorisationId
            accountId shouldBe someAccountUid()
        }
        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            body containsProp json {
                "holdId" To something
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 4_370
                "placedAt" To "2026-06-15T10:30:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun aReplacedCardDeclinesWhileItsReplacementAuthorises() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = cardPan,
            variant = DEBIT,
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse()
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(4_370)))
        and(nowIs("2026-06-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(replacementCardId))
        and(theNextGeneratedPanWillBe(replacementPan))
        and(theNextGeneratedCvvWillBe("456"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/report-lost-or-stolen")
                .header("Idempotency-Key", "loop-report-lost-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cancelledCardId" To someCardUid().toString()
                "replacementCard" To json {
                    "cardId" To replacementCardId.toString()
                    "accountId" To someAccountUid().toString()
                    "maskedPan" To "************5599"
                    "variant" To "DEBIT"
                    "status" To "ACTIVE"
                    "expiry" To "06/29"
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        whenever(cardServiceReceives(anAuthorisationRequestFor(cardPan, declinedSchemeReference)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To something
                "decision" To "DECLINED"
                "declineReason" To "CARD_CANCELLED"
                noExtraProps()
            }
        }
        and(theAuthorisationForSchemeReference(declinedSchemeReference)) {
            status shouldBe DECLINED
            declineReason shouldBe CARD_CANCELLED
            holdId shouldBe null
            cardId shouldBe someCardUid()
        }
        and(noLedgerHoldWasPlaced())

        whenever(cardServiceReceives(anAuthorisationRequestFor(replacementPan, approvedSchemeReference)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To something
                "decision" To "APPROVED"
                noExtraProps()
            }
        }
        and(theAuthorisationForSchemeReference(approvedSchemeReference)) {
            status shouldBe AUTHORISED
            declineReason shouldBe null
            holdId shouldBe authorisationId
            cardId shouldBe replacementCardId
        }
    }

    private fun anAuthorisationRequestFor(pan: String, schemeReference: UUID) =
        post()
            .url("/api/v1/internal/cards/authorisations")
            .header("Content-Type", "application/json")
            .withServiceJwtToken()
            .body(
                """
                {
                  "schemeReference": "$schemeReference",
                  "pan": "$pan",
                  "amountMinorUnits": 4370,
                  "currency": "GBP",
                  "merchantName": "Fictional Coffee Co",
                  "mcc": "5814"
                }
                """.trimIndent()
            )
}
