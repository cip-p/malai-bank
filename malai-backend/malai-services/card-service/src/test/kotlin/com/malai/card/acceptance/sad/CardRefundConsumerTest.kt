package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardRefundConsumerTest : CardAcceptanceTest() {

    private val settledAuthorisationId = UUID.fromString("00000000-0000-4000-7000-000000000092")
    private val settledSchemeReference = UUID.fromString("00000000-0000-4000-8000-000000000092")
    private val validRefundReference = UUID.fromString("00000000-0000-4000-9000-000000000092")
    private val refundTransactionId = UUID.fromString("00000000-0000-4000-a000-000000000092")

    private fun aStubbedPostTransaction() = postTransactionReturns(aPostTransactionResponse()
        .withTransactionId(refundTransactionId)
        .withPostedAt("2026-10-15T10:30:00Z"))

    private fun aSettledPurchaseExists() = anAuthorisationExists(
        authorisationId = settledAuthorisationId,
        cardId = someCardUid(),
        accountId = someAccountUid(),
        accountHolderId = someAccountHolderUid(),
        schemeReference = settledSchemeReference,
        merchantName = "Harbour Fuel",
        amountMinorUnits = 12_000,
        status = SETTLED,
    )

    @Test
    fun refundReferencingAnUnknownPurchaseIsIgnoredWithNoPosting() = malaiAcceptanceTest {
        val unknownSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
        val unknownRefundReference = UUID.fromString("00000000-0000-4000-9000-0000000000a1")
        given(thereAreNoAuthorisations())
        and(thereAreNoRefunds())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theNextGeneratedUuidWillBe(refundTransactionId))
        and(aStubbedPostTransaction())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(aSettledPurchaseExists())

        and(theSchemeRefunds(unknownSchemeReference, unknownRefundReference, refundAmountMinorUnits = 999, groupId = "refund-barrier"))
        and(theSchemeRefunds(settledSchemeReference, validRefundReference, refundAmountMinorUnits = 4_370, groupId = "refund-barrier"))

        then(theRefundOnceRecorded(validRefundReference)) {
            this.amountMinorUnits shouldBe 4_370
        }

        and(theRefundsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/api/v1/internal/ledger/transactions")
        }) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun refundReferencingANonSettledPurchaseIsIgnoredWithNoPosting() = malaiAcceptanceTest {
        val authorisedAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000a2")
        val authorisedSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000a2")
        val rejectedRefundReference = UUID.fromString("00000000-0000-4000-9000-0000000000a2")
        given(thereAreNoAuthorisations())
        and(thereAreNoRefunds())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(theNextGeneratedUuidWillBe(refundTransactionId))
        and(aStubbedPostTransaction())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(aSettledPurchaseExists())
        and(anAuthorisationExists(
            authorisationId = authorisedAuthorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = authorisedSchemeReference,
            amountMinorUnits = 5_000,
            status = AUTHORISED,
        ))

        and(theSchemeRefunds(authorisedSchemeReference, rejectedRefundReference, refundAmountMinorUnits = 5_000, groupId = "refund-barrier"))
        and(theSchemeRefunds(settledSchemeReference, validRefundReference, refundAmountMinorUnits = 4_370, groupId = "refund-barrier"))

        then(theRefundOnceRecorded(validRefundReference)) {
            this.amountMinorUnits shouldBe 4_370
        }

        and(theRefundsInTheDatabase()) {
            this shouldHaveSize 1
            this.single().refundReference shouldBe validRefundReference
        }

        and(theAuthorisationInTheDatabase(authorisedAuthorisationId)) {
            this.status shouldBe AUTHORISED
        }

        and(cardServiceMadeTheFollowingHttpRequests {
            url.endsWith("/api/v1/internal/ledger/transactions")
        }) {
            this shouldHaveSize 1
        }
    }
}
