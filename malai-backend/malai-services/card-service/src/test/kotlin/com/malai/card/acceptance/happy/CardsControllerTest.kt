package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardVariant.DEBIT
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class CardsControllerTest : CardAcceptanceTest() {

    private val olderCardId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")

    @Test
    fun listReturnsTheHoldersCardsWithMaskedPanNewestFirst() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000012341",
            expiry = "10/29",
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))
        and(aCardExists(
            cardId = olderCardId,
            accountId = anotherAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000009876",
            expiry = "07/29",
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(aCardExists(
            cardId = UUID.randomUUID(),
            accountId = UUID.randomUUID(),
            accountHolderId = anotherAccountHolderUid(),
            pan = "9904320000005555",
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/cards")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cards" To listOf(
                    json {
                        "cardId" To someCardUid().toString()
                        "accountId" To someAccountUid().toString()
                        "maskedPan" To "************2341"
                        "variant" To "DEBIT"
                        "status" To "ACTIVE"
                        "expiry" To "10/29"
                        noExtraProps()
                    },
                    json {
                        "cardId" To olderCardId.toString()
                        "accountId" To anotherAccountUid().toString()
                        "maskedPan" To "************9876"
                        "variant" To "DEBIT"
                        "status" To "ACTIVE"
                        "expiry" To "07/29"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun detailRevealsTheFullPanExpiryAndCvv() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            cardholderName = "Sarah Mitchell",
            pan = "9904320000012341",
            expiry = "10/29",
            cvv = "123",
            variant = DEBIT,
            status = ACTIVE,
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/cards/${someCardUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cardId" To someCardUid().toString()
                "accountId" To someAccountUid().toString()
                "cardholderName" To "Sarah Mitchell"
                "pan" To "9904320000012341"
                "expiry" To "10/29"
                "cvv" To "123"
                "variant" To "DEBIT"
                "status" To "ACTIVE"
                noExtraProps()
            }
        }
    }
}
