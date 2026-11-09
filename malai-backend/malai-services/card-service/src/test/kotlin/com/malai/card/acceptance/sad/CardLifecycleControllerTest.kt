package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardEntity
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardStatus.FROZEN
import com.malai.card.model.CardVariant.DEBIT
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.NOT_FOUND
import java.time.Instant

class CardLifecycleControllerTest : CardAcceptanceTest() {

    @Test
    fun freezeAnAlreadyFrozenCardIsAConflictAndLeavesTheCardUnchanged() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            cardholderName = "Sarah Mitchell",
            pan = "9904320000012341",
            expiry = "10/29",
            cvv = "123",
            variant = DEBIT,
            status = FROZEN,
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/freeze")
                .header("Idempotency-Key", "sad-freeze-frozen-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6993"
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
                status = FROZEN,
                createdAt = Instant.parse("2026-05-01T09:00:00Z"),
            )
        }
    }

    @Test
    fun unfreezeAnActiveCardIsAConflictAndLeavesTheCardUnchanged() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
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
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/unfreeze")
                .header("Idempotency-Key", "sad-unfreeze-active-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6993"
            }
        }

        and(theCardInTheDatabase(someCardUid())) {
            this.status shouldBe ACTIVE
        }
    }

    @Test
    fun reportLostOnACancelledCardIsAConflictAndIssuesNoReplacement() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000012341",
            variant = DEBIT,
            status = CANCELLED,
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/report-lost-or-stolen")
                .header("Idempotency-Key", "sad-report-cancelled-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CONFLICT
            body containsProp json {
                "message" To "6993"
            }
        }

        and(theCardsForAccountHolder(someAccountHolderUid())) {
            this shouldHaveSize 1
            this[0].status shouldBe CANCELLED
        }
    }

    @Test
    fun freezeAnUnknownCardIsNotFound() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/freeze")
                .header("Idempotency-Key", "sad-freeze-unknown-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    @Test
    fun freezeACardBelongingToAnotherHolderIsNotFound() = malaiAcceptanceTest {
        given(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
            pan = "9904320000012341",
            variant = DEBIT,
            status = ACTIVE,
        ))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/freeze")
                .header("Idempotency-Key", "sad-freeze-foreign-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }

        and(theCardInTheDatabase(someCardUid())) {
            this.status shouldBe ACTIVE
        }
    }
}
