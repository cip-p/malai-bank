package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.entity.CardEntity
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardStatus.FROZEN
import com.malai.card.model.CardVariant.DEBIT
import com.malai.commons.model.Currency
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class CardLifecycleControllerTest : CardAcceptanceTest() {

    private val replacementCardId = UUID.fromString("00000000-0000-4000-7000-0000000000b1")
    private val existingAuthorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000b2")
    private val existingSchemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000b3")

    @Test
    fun freezeMovesAnActiveCardToFrozenAndWritesAnAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
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
                .url("/api/v1/cards/${someCardUid()}/freeze")
                .header("Idempotency-Key", "freeze-key")
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

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "CARD_FROZEN"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Card"
            this[0]["resource_id"] shouldBe someCardUid().toString()
        }
    }

    @Test
    fun unfreezeMovesAFrozenCardBackToActiveAndWritesAnAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
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
                .url("/api/v1/cards/${someCardUid()}/unfreeze")
                .header("Idempotency-Key", "unfreeze-key")
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
                createdAt = Instant.parse("2026-05-01T09:00:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "CARD_UNFROZEN"
            this[0]["resource_id"] shouldBe someCardUid().toString()
        }
    }

    @Test
    fun reportLostCancelsTheCardAndIssuesAReplacementLeavingApprovedAuthorisationsUntouched() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(thereAreNoAuthorisations())
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
        and(anAuthorisationExists(
            authorisationId = existingAuthorisationId,
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            schemeReference = existingSchemeReference,
            merchantName = "Fictional Coffee Co",
            mcc = "5814",
            amountMinorUnits = 4_370,
            status = AUTHORISED,
            holdId = existingAuthorisationId,
            createdAt = Instant.parse("2026-05-02T09:00:00Z"),
        ))
        and(nowIs("2026-06-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(replacementCardId))
        and(theNextGeneratedPanWillBe("9904329999995599"))
        and(theNextGeneratedCvvWillBe("456"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/report-lost-or-stolen")
                .header("Idempotency-Key", "report-lost-key")
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
                status = CANCELLED,
                createdAt = Instant.parse("2026-05-01T09:00:00Z"),
            )
        }

        and(theCardInTheDatabase(replacementCardId)) {
            this shouldBe CardEntity(
                cardId = replacementCardId,
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                cardholderName = "Sarah Mitchell",
                pan = "9904329999995599",
                expiry = "06/29",
                cvv = "456",
                variant = DEBIT,
                status = ACTIVE,
                createdAt = Instant.parse("2026-06-15T10:30:00Z"),
            )
        }

        and(theCardsForAccountHolder(someAccountHolderUid())) {
            this shouldHaveSize 2
            this.count { it.status == ACTIVE } shouldBe 1
            this.single { it.status == ACTIVE }.cardId shouldBe replacementCardId
        }

        and(theAuthorisationInTheDatabase(existingAuthorisationId)) {
            this shouldBe CardAuthorisationEntity(
                authorisationId = existingAuthorisationId,
                cardId = someCardUid(),
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                schemeReference = existingSchemeReference,
                merchantName = "Fictional Coffee Co",
                mcc = "5814",
                amountMinorUnits = 4_370,
                currency = Currency.GBP,
                status = AUTHORISED,
                declineReason = null,
                holdId = existingAuthorisationId,
                createdAt = Instant.parse("2026-05-02T09:00:00Z"),
            )
        }

        and(theLastAuditEventWithAction("CARD_REPORTED_LOST_OR_STOLEN")) {
            this["resource_id"] shouldBe someCardUid().toString()
            (this["after_state"] as String) shouldContain "[REDACTED]"
            (this["after_state"] as String) shouldContain replacementCardId.toString()
            (this["after_state"] as String) shouldNotContain "9904329999995599"
        }
    }

    @Test
    fun reportLostReplayWithTheSameKeyReturnsTheCachedOutcomeAndIssuesNoSecondReplacement() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            pan = "9904320000012341",
            variant = DEBIT,
            status = ACTIVE,
            createdAt = Instant.parse("2026-05-01T09:00:00Z"),
        ))
        and(nowIs("2026-06-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(replacementCardId))
        and(theNextGeneratedPanWillBe("9904329999995599"))
        and(theNextGeneratedCvvWillBe("456"))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/report-lost-or-stolen")
                .header("Idempotency-Key", "report-lost-replay-key")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        whenever(cardServiceReceives(
            post()
                .url("/api/v1/cards/${someCardUid()}/report-lost-or-stolen")
                .header("Idempotency-Key", "report-lost-replay-key")
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

        and(theCardsForAccountHolder(someAccountHolderUid())) {
            this shouldHaveSize 2
            this.count { it.status == ACTIVE } shouldBe 1
        }

        and(theNumberOfAuditEventsWithAction("CARD_REPORTED_LOST_OR_STOLEN")) {
            this shouldBe 1L
        }
    }
}
