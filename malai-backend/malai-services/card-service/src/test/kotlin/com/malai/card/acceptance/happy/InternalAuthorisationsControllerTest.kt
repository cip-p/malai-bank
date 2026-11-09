package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.AuthoriseCardRequest
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.card.model.CardVariant.DEBIT
import com.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.commons.model.Currency
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalAuthorisationsControllerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000a1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000b1")

    @Test
    fun approvesAnActiveDebitCardPlacingALedgerHoldAndPersistingTheAuthorisation() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(thereAreNoAuthorisations())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000012341",
            variant = DEBIT,
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(placeHoldReturns(aHoldResponse()
            .withHoldId(authorisationId)
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(4_370)))
        and(theNextGeneratedUuidWillBe(authorisationId))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/internal/cards/authorisations")
                .header("Content-Type", "application/json")
                .withServiceJwtToken()
                .body(testingObjectMapper.writeValueAsString(AuthoriseCardRequest(
                    schemeReference = schemeReference,
                    pan = "9904320000012341",
                    amountMinorUnits = 4_370,
                    currency = "GBP",
                    merchantName = "Fictional Coffee Co",
                    mcc = "5814",
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To authorisationId.toString()
                "decision" To "APPROVED"
                noExtraProps()
            }
        }

        and(theAuthorisationInTheDatabase(authorisationId)) {
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
                status = AUTHORISED,
                declineReason = null,
                holdId = authorisationId,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
            body containsProp json {
                "holdId" To authorisationId.toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 4_370
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }
    }

    @Test
    fun listsTheAccountsAuthorisedAndDeclinedAuthorisationsNewestFirstExcludingSettled() = malaiAcceptanceTest {
        val pendingId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")
        val declinedId = UUID.fromString("00000000-0000-4000-7000-0000000000c2")
        val settledId = UUID.fromString("00000000-0000-4000-7000-0000000000c3")
        val otherAccountId = UUID.fromString("00000000-0000-4000-9000-0000000000d1")
        val otherAccountAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000c4")

        given(thereAreNoAuthorisations())
        and(anAuthorisationExists(
            authorisationId = pendingId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e1"),
            merchantName = "Fictional Coffee Co",
            amountMinorUnits = 4_370,
            status = AUTHORISED,
            createdAt = Instant.parse("2026-10-15T10:00:00Z"),
        ))
        and(anAuthorisationExists(
            authorisationId = declinedId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e2"),
            merchantName = "Fictional Books Ltd",
            amountMinorUnits = 1_599,
            status = DECLINED,
            declineReason = CARD_FROZEN,
            holdId = null,
            createdAt = Instant.parse("2026-10-15T11:00:00Z"),
        ))
        and(anAuthorisationExists(
            authorisationId = settledId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e3"),
            merchantName = "Fictional Coffee Co",
            amountMinorUnits = 4_370,
            status = SETTLED,
            holdId = null,
            createdAt = Instant.parse("2026-10-15T12:00:00Z"),
        ))
        and(anAuthorisationExists(
            authorisationId = otherAccountAuthorisationId,
            cardId = UUID.fromString("00000000-0000-4000-a000-0000000000f1"),
            accountId = otherAccountId,
            accountHolderId = someAccountHolderUid(),
            schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000e4"),
            merchantName = "Fictional Fuel PLC",
            amountMinorUnits = 9_000,
            status = AUTHORISED,
            createdAt = Instant.parse("2026-10-15T13:00:00Z"),
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/authorisations")
                .queryParam("accountId", someAccountUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisations" To listOf(
                    json {
                        "authorisationId" To declinedId.toString()
                        "cardId" To someCardUid().toString()
                        "status" To "DECLINED"
                        "merchantName" To "Fictional Books Ltd"
                        "amountMinorUnits" To 1_599
                        "currency" To "GBP"
                        "declineReason" To "CARD_FROZEN"
                        "createdAt" To "2026-10-15T11:00:00Z"
                        noExtraProps()
                    },
                    json {
                        "authorisationId" To pendingId.toString()
                        "cardId" To someCardUid().toString()
                        "status" To "AUTHORISED"
                        "merchantName" To "Fictional Coffee Co"
                        "amountMinorUnits" To 4_370
                        "currency" To "GBP"
                        "createdAt" To "2026-10-15T10:00:00Z"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun resolvesASettledAuthorisationsSchemeReferenceForTheOwningHolder() = malaiAcceptanceTest {
        val settledId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")
        val settledSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000f2")

        given(thereAreNoAuthorisations())
        and(anAuthorisationExists(
            authorisationId = settledId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = settledSchemeReference,
            merchantName = "Fictional Coffee Co",
            amountMinorUnits = 4_370,
            status = SETTLED,
            holdId = null,
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/authorisations/$settledId")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "authorisationId" To settledId.toString()
                "schemeReference" To settledSchemeReference.toString()
                "status" To "SETTLED"
                "amountMinorUnits" To 4_370
                "currency" To "GBP"
                noExtraProps()
            }
        }
    }
}
