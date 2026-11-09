package com.malai.card.support

import com.malai.card.entity.CardAuthorisationEntity
import com.malai.card.entity.CardEntity
import com.malai.card.entity.CardRefundEntity
import com.malai.card.messages.CardPresentmentNotify
import com.malai.card.messages.CardRefundNotify
import com.malai.card.messages.CardReversalNotify
import com.malai.card.model.CardAuthorisationStatus
import com.malai.card.model.CardAuthorisationStatus.AUTHORISED
import com.malai.card.model.CardAuthorisationStatus.DECLINED
import com.malai.card.model.CardAuthorisationStatus.REVERSED
import com.malai.card.model.CardStatus
import com.malai.card.model.CardVariant
import com.malai.card.model.DeclineReason
import com.malai.card.repository.CardAuthorisationRepository
import com.malai.card.repository.CardRefundRepository
import com.malai.card.repository.CardRepository
import com.malai.commons.model.Currency
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.sqs.cards.CardsQueues
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.audit.AuditTestSupport
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.StateCollector
import io.awspring.cloud.sqs.operations.SqsTemplate
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.util.UUID

interface WithCardService : WithApplicationContext, AuditTestSupport {

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    fun cardRepository(): CardRepository = getBean(CardRepository::class.java)

    fun cardAuthorisationRepository(): CardAuthorisationRepository = getBean(CardAuthorisationRepository::class.java)

    fun cardRefundRepository(): CardRefundRepository = getBean(CardRefundRepository::class.java)

    fun thereAreNoCards() = CallableAction {
        cardRepository().deleteAll()
    }

    fun thereAreNoAuthorisations() = CallableAction {
        cardAuthorisationRepository().deleteAll()
    }

    fun thereAreNoRefunds() = CallableAction {
        cardRefundRepository().deleteAll()
    }

    fun anAuthorisationExists(
        authorisationId: UUID,
        cardId: UUID,
        accountId: UUID,
        accountHolderId: UUID,
        schemeReference: UUID,
        merchantName: String = "Fictional Coffee Co",
        mcc: String = "5814",
        amountMinorUnits: Long,
        currency: Currency = Currency.GBP,
        status: CardAuthorisationStatus = CardAuthorisationStatus.AUTHORISED,
        declineReason: DeclineReason? = null,
        holdId: UUID? = authorisationId,
        createdAt: Instant = Instant.parse("2026-10-15T10:30:00Z"),
    ) = CallableAction {
        cardAuthorisationRepository().save(
            CardAuthorisationEntity(
                authorisationId = authorisationId,
                cardId = cardId,
                accountId = accountId,
                accountHolderId = accountHolderId,
                schemeReference = schemeReference,
                merchantName = merchantName,
                mcc = mcc,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                status = status,
                declineReason = declineReason,
                holdId = holdId,
                createdAt = createdAt,
            )
        )
    }

    fun theSchemePresents(
        schemeReference: UUID,
        presentedAmountMinorUnits: Long,
        merchantName: String = "Fictional Coffee Co",
        currency: String = "GBP",
        groupId: String = schemeReference.toString(),
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_PRESENTED)
        val notify = CardPresentmentNotify(
            schemeReference = schemeReference,
            presentedAmountMinorUnits = presentedAmountMinorUnits,
            currency = currency,
            merchantName = merchantName,
        )
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<CardPresentmentNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(groupId)
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.CardService,
            CardsQueues.SCHEME_EVENT_PRESENTED,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to groupId,
                "MessageDeduplicationId" to dedupId,
            ),
        )
    }

    fun theAuthorisationOnceSettled(authorisationId: UUID) = StateCollector<CardAuthorisationEntity> { _ ->
        eventually {
            cardAuthorisationRepository().findById(authorisationId)
                .orElseThrow { AssertionError("Authorisation $authorisationId not found in database") }
                .status shouldBe CardAuthorisationStatus.SETTLED
        }
        cardAuthorisationRepository().findById(authorisationId).orElseThrow()
    }

    fun theSchemeReverses(
        schemeReference: UUID,
        groupId: String = schemeReference.toString(),
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_REVERSED)
        val notify = CardReversalNotify(schemeReference = schemeReference)
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<CardReversalNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(groupId)
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.CardService,
            CardsQueues.SCHEME_EVENT_REVERSED,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to groupId,
                "MessageDeduplicationId" to dedupId,
            ),
        )
    }

    fun theSchemeRefunds(
        schemeReference: UUID,
        refundReference: UUID,
        refundAmountMinorUnits: Long,
        currency: String = "GBP",
        groupId: String = schemeReference.toString(),
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, CardsQueues.SCHEME_EVENT_REFUNDED)
        val notify = CardRefundNotify(
            schemeReference = schemeReference,
            refundReference = refundReference,
            refundAmountMinorUnits = refundAmountMinorUnits,
            currency = currency,
        )
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<CardRefundNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(groupId)
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.CardService,
            CardsQueues.SCHEME_EVENT_REFUNDED,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to groupId,
                "MessageDeduplicationId" to dedupId,
            ),
        )
    }

    fun theAuthorisationOnceReversed(authorisationId: UUID) = StateCollector<CardAuthorisationEntity> { _ ->
        eventually {
            cardAuthorisationRepository().findById(authorisationId)
                .orElseThrow { AssertionError("Authorisation $authorisationId not found in database") }
                .status shouldBe REVERSED
        }
        cardAuthorisationRepository().findById(authorisationId).orElseThrow()
    }

    fun theRefundOnceRecorded(refundReference: UUID) = StateCollector<CardRefundEntity> { _ ->
        eventually {
            check(cardRefundRepository().existsByRefundReference(refundReference)) {
                "Refund $refundReference not found in database"
            }
        }
        cardRefundRepository().findById(refundReference).orElseThrow()
    }

    fun theRefundInTheDatabase(refundReference: UUID) = StateCollector<CardRefundEntity> { _ ->
        cardRefundRepository().findById(refundReference)
            .orElseThrow { AssertionError("Refund $refundReference not found in database") }
    }

    fun theRefundsInTheDatabase() = StateCollector { _ ->
        cardRefundRepository().findAll()
    }

    fun theInFlightAuthorisationsForAccount(accountId: UUID) = StateCollector { _ ->
        cardAuthorisationRepository().findByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(AUTHORISED, DECLINED))
    }

    fun aCardExists(
        cardId: UUID,
        accountId: UUID,
        accountHolderId: UUID,
        cardholderName: String = "Some Cardholder",
        pan: String = "9904320000000000",
        expiry: String = "10/29",
        cvv: String = "000",
        variant: CardVariant = CardVariant.DEBIT,
        status: CardStatus = CardStatus.ACTIVE,
        createdAt: Instant = Instant.parse("2026-10-15T10:30:00Z"),
    ) = CallableAction {
        cardRepository().save(
            CardEntity(
                cardId = cardId,
                accountId = accountId,
                accountHolderId = accountHolderId,
                cardholderName = cardholderName,
                pan = pan,
                expiry = expiry,
                cvv = cvv,
                variant = variant,
                status = status,
                createdAt = createdAt,
            )
        )
    }

    fun theCardInTheDatabase(cardId: UUID) = StateCollector<CardEntity> { _ ->
        cardRepository().findById(cardId)
            .orElseThrow { AssertionError("Card $cardId not found in database") }
    }

    fun theCardsForAccountHolder(accountHolderId: UUID) = StateCollector { _ ->
        cardRepository().findByAccountHolderIdOrderByCreatedAtDesc(accountHolderId)
    }

    fun theCardsInTheDatabase() = StateCollector { _ ->
        cardRepository().findAll()
    }

    fun theAuthorisationInTheDatabase(authorisationId: UUID) = StateCollector<CardAuthorisationEntity> { _ ->
        cardAuthorisationRepository().findById(authorisationId)
            .orElseThrow { AssertionError("Authorisation $authorisationId not found in database") }
    }

    fun theAuthorisationForSchemeReference(schemeReference: UUID) = StateCollector<CardAuthorisationEntity> { _ ->
        cardAuthorisationRepository().findBySchemeReference(schemeReference)
            ?: throw AssertionError("Authorisation for scheme reference $schemeReference not found in database")
    }

    fun theAuthorisationsInTheDatabase() = StateCollector { _ ->
        cardAuthorisationRepository().findAll()
    }
}
