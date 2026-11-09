package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardRefundEntity
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.commons.model.Currency
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.util.UUID

class CardRefundConsumerTest : CardAcceptanceTest() {

    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-000000000091")
    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-000000000091")
    private val refundReference = UUID.fromString("00000000-0000-4000-9000-000000000091")
    private val refundTransactionId = UUID.fromString("00000000-0000-4000-a000-000000000091")

    @Test
    fun refundCreditsTheCardAccountWithTheOriginalMerchantNameAndRecordsTheRefund() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(thereAreNoRefunds())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theNextGeneratedUuidWillBe(refundTransactionId))
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(refundTransactionId)
            .withPostedAt("2026-10-15T10:30:00Z")))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            merchantName = "Harbour Fuel",
            amountMinorUnits = 12_000,
            status = SETTLED,
        ))

        and(theSchemeRefunds(schemeReference, refundReference, refundAmountMinorUnits = 4_370))

        then(theRefundOnceRecorded(refundReference)) {
            this shouldBe CardRefundEntity(
                refundReference = refundReference,
                authorisationId = authorisationId,
                accountId = someAccountUid(),
                schemeReference = schemeReference,
                merchantName = "Harbour Fuel",
                amountMinorUnits = 4_370,
                currency = Currency.GBP,
                transactionId = refundTransactionId,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }

        and(cardServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
            body containsProp json {
                "transactionId" To something
                "type" To "CARD_REFUND"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "Harbour Fuel"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To 4_370
                        "counterpartyId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "description" To "Harbour Fuel"
                        "merchantName" To "Harbour Fuel"
                        noExtraProps()
                    },
                    json {
                        "accountId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "amountMinorUnits" To -4_370
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
    fun redeliveryOfTheSameRefundCreditsTheAccountOnlyOnce() = malaiAcceptanceTest {
        given(thereAreNoAuthorisations())
        and(thereAreNoRefunds())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theNextGeneratedUuidWillBe(refundTransactionId))
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(refundTransactionId)
            .withPostedAt("2026-10-15T10:30:00Z")))
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuthorisationExists(
            authorisationId = authorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            merchantName = "Harbour Fuel",
            amountMinorUnits = 12_000,
            status = SETTLED,
        ))

        and(theSchemeRefunds(schemeReference, refundReference, refundAmountMinorUnits = 4_370))
        and(theSchemeRefunds(schemeReference, refundReference, refundAmountMinorUnits = 4_370))

        then(theRefundOnceRecorded(refundReference)) {
            this.amountMinorUnits shouldBe 4_370
        }

        and(theRefundsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            this shouldHaveSize 1
        }
    }

    companion object {
        private val SCHEME_SETTLEMENT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8001-000000010002")
    }
}
