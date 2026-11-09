package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardVariant.DEBIT
import com.malai.card.model.IssueCardRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalCardsControllerTest : CardAcceptanceTest() {

    private val wouldBeCardId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")

    @Test
    fun issueDebitCardAgainstAnAccount() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(theNextGeneratedUuidWillBe(someCardUid()))
        and(theNextGeneratedPanWillBe("9904320000012341"))
        and(theNextGeneratedCvvWillBe("123"))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/internal/cards")
                .header("Content-Type", "application/json")
                .withServiceJwtToken()
                .body(testingObjectMapper.writeValueAsString(IssueCardRequest(
                    accountId = someAccountUid(),
                    accountHolderId = someAccountHolderUid(),
                    cardholderName = "Sarah Mitchell",
                    variant = DEBIT,
                )))
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

        and(theCardInTheDatabase(someCardUid())) {
            this shouldBe CardEntity(
                cardId = someCardUid(),
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                cardholderName = "Sarah Mitchell",
                pan = "9904320000012341",
                expiry = "10/29",
                cvv = "123",
                variant = DEBIT,
                status = ACTIVE,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
            )
        }
    }

    @Test
    fun issuingWhenAnAccountAlreadyHasAnActiveCardReturnsTheExistingCard() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            cardholderName = "Sarah Mitchell",
            pan = "9904320000012341",
            expiry = "10/29",
            cvv = "123",
        ))
        and(theNextGeneratedUuidWillBe(wouldBeCardId))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/internal/cards")
                .header("Content-Type", "application/json")
                .withServiceJwtToken()
                .body(testingObjectMapper.writeValueAsString(IssueCardRequest(
                    accountId = someAccountUid(),
                    accountHolderId = someAccountHolderUid(),
                    cardholderName = "Sarah Mitchell",
                    variant = DEBIT,
                )))
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

        and(theCardsForAccountHolder(someAccountHolderUid())) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun resolvesACardsAccountAndPanForTheOwningHolder() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000012341",
            variant = DEBIT,
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/${someCardUid()}")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cardId" To someCardUid().toString()
                "accountId" To someAccountUid().toString()
                "pan" To "9904320000012341"
                noExtraProps()
            }
        }
    }
}
