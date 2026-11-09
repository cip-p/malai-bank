package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.card.service.CardPresentmentService.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.malai.commons.model.Currency
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.util.UUID

class CardPresentmentConsumerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000d1")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000d1")

    @Test
    fun settlesTheHoldForThePresentedAmountAndMovesTheAuthorisationToSettled() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(authorisationId)
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370))

        then(theAuthorisationOnceSettled(authorisationId)) {
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
                status = SETTLED,
                declineReason = null,
                holdId = authorisationId,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/settle"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
            body containsProp json {
                "transactionId" To something
                "type" To "CARD_PRESENTMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "Fictional Coffee Co"
                "amountMinorUnits" To 4_370
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -4_370
                        "counterpartyId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "description" To "Fictional Coffee Co"
                        "merchantName" To "Fictional Coffee Co"
                        noExtraProps()
                    },
                    json {
                        "accountId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "amountMinorUnits" To 4_370
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "Fictional Coffee Co"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun partialPresentmentSettlesForThePresentedAmountLeavingTheAuthorisedAmountUnchanged() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(authorisationId)
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            merchantName = "Harbour Fuel",
            amountMinorUnits = 12_000,
        ))

        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370, merchantName = "Harbour Fuel"))

        then(theAuthorisationOnceSettled(authorisationId)) {
            this.status shouldBe SETTLED
            this.amountMinorUnits shouldBe 12_000
        }

        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/settle"
        }) {
            body containsProp json {
                "transactionId" To something
                "type" To "CARD_PRESENTMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "Harbour Fuel"
                "amountMinorUnits" To 4_370
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -4_370
                        "counterpartyId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "description" To "Harbour Fuel"
                        "merchantName" To "Harbour Fuel"
                        noExtraProps()
                    },
                    json {
                        "accountId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "amountMinorUnits" To 4_370
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "Harbour Fuel"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun redeliveryOfTheSamePresentmentSettlesOnlyOnce() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(authorisationId)
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            amountMinorUnits = 4_370,
        ))

        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370))
        and(theSchemePresents(schemeReference, presentedAmountMinorUnits = 4_370))

        then(theAuthorisationOnceSettled(authorisationId)) {
            this.status shouldBe SETTLED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/holds/$authorisationId/settle"
        }) {
            this shouldHaveSize 1
        }
    }
}
