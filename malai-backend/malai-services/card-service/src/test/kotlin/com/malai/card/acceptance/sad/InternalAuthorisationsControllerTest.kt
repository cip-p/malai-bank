package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.CardStatus
import com.malai.card.model.CardVariant.DEBIT
import com.malai.card.model.DeclineReason
import com.malai.card.model.DeclineReason.CARD_CANCELLED
import com.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.card.model.DeclineReason.INSUFFICIENT_FUNDS
import com.malai.card.model.DeclineReason.UNKNOWN_CARD
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.commons.model.Currency
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalAuthorisationsControllerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000d1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e1")
    private val pan = "9904320000012341"

    @Test
    fun declinesAFrozenCardWithoutCallingTheLedger() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = pan,
            status = CardStatus.FROZEN,
        ))
        and(theNextGeneratedUuidWillBe(authorisationId))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(anAuthorisationRequestFor(pan)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To authorisationId.toString()
                "decision" To "DECLINED"
                "declineReason" To "CARD_FROZEN"
                noExtraProps()
            }
        }
        and(theAuthorisationInTheDatabase(authorisationId)) {
            this shouldBe aDeclinedAuthorisation(CARD_FROZEN, someCardUid(), someAccountUid(), someAccountHolderUid())
        }
        and(noLedgerHoldWasPlaced())
    }

    @Test
    fun declinesACancelledCardWithoutCallingTheLedger() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = pan,
            status = CardStatus.CANCELLED,
        ))
        and(theNextGeneratedUuidWillBe(authorisationId))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(anAuthorisationRequestFor(pan)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To authorisationId.toString()
                "decision" To "DECLINED"
                "declineReason" To "CARD_CANCELLED"
                noExtraProps()
            }
        }
        and(theAuthorisationInTheDatabase(authorisationId)) {
            this shouldBe aDeclinedAuthorisation(CARD_CANCELLED, someCardUid(), someAccountUid(), someAccountHolderUid())
        }
        and(noLedgerHoldWasPlaced())
    }

    @Test
    fun declinesWhenTheLedgerRejectsTheHoldOnAvailableFunds() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = pan,
            variant = DEBIT,
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturnsInsufficientFunds())
        and(theNextGeneratedUuidWillBe(authorisationId))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(anAuthorisationRequestFor(pan)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To authorisationId.toString()
                "decision" To "DECLINED"
                "declineReason" To "INSUFFICIENT_FUNDS"
                noExtraProps()
            }
        }
        and(theAuthorisationInTheDatabase(authorisationId)) {
            this shouldBe aDeclinedAuthorisation(INSUFFICIENT_FUNDS, someCardUid(), someAccountUid(), someAccountHolderUid())
        }
        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
        }
    }

    @Test
    fun declinesAnUnknownPanWithoutCallingTheLedger() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(theNextGeneratedUuidWillBe(authorisationId))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(anAuthorisationRequestFor("9904320000009999")))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To authorisationId.toString()
                "decision" To "DECLINED"
                "declineReason" To "UNKNOWN_CARD"
                noExtraProps()
            }
        }
        and(theAuthorisationInTheDatabase(authorisationId)) {
            this shouldBe aDeclinedAuthorisation(UNKNOWN_CARD, cardId = null, accountId = null, accountHolderId = null)
        }
        and(noLedgerHoldWasPlaced())
    }

    @Test
    fun resolveForAnUnknownAuthorisationIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/authorisations/$authorisationId")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    @Test
    fun resolveForAnAuthorisationBelongingToAnotherHolderIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
            status = SETTLED,
            holdId = null,
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/authorisations/$authorisationId")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    private fun anAuthorisationRequestFor(pan: String) =
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

    private fun aDeclinedAuthorisation(
        reason: DeclineReason,
        cardId: UUID?,
        accountId: UUID?,
        accountHolderId: UUID?,
    ) = CardAuthorisationEntity(
        authorisationId = authorisationId,
        cardId = cardId,
        accountId = accountId,
        accountHolderId = accountHolderId,
        schemeReference = schemeReference,
        merchantName = "Fictional Coffee Co",
        mcc = "5814",
        amountMinorUnits = 4_370,
        currency = Currency.GBP,
        status = DECLINED,
        declineReason = reason,
        holdId = null,
        createdAt = Instant.parse("2026-10-15T10:30:00Z"),
    )
}
