package com.malai.payments.support

import com.malai.client.malai.MalaiTestFixtures
import com.malai.client.malai.accounts.lookup.WithAccountLookup
import com.malai.commons.banking.MalaiSortCode
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.BacsCollectionReturn
import com.malai.payments.messages.BacsInboundNotify
import com.malai.payments.messages.FpsInboundNotify
import com.malai.payments.messages.FpsInboundReturned
import com.malai.payments.messages.FpsOutboundReturned
import com.malai.payments.messages.FpsOutboundSettled
import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.payments.messages.ReturnReason
import com.malai.payments.repository.PaymentRepository
import com.malai.payments.messages.SwiftOutboundSettled
import com.malai.sqs.MalaiSqsMessagingProperties
import com.malai.sqs.QueueNaming
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.payments.entity.PaymentState
import com.malai.sqs.payments.PaymentsQueues
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.eventually
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.SqsInteractionCapturer
import com.malai.test.acceptance.kensa.testingObjectMapper
import io.awspring.cloud.sqs.operations.SqsTemplate
import io.kotest.matchers.shouldBe
import com.malai.payments.dedup.SqsMessageDedupId
import com.malai.payments.dedup.SqsMessageDedupRepository
import com.malai.payments.scheme.DirectDebitCollectionProcessor
import com.malai.payments.scheme.InboundPaymentProcessor
import com.malai.payments.screening.ScreeningMode
import com.malai.payments.screening.ScreeningProperties
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

interface WithPayments : WithApplicationContext, WithAccountLookup, MalaiTestFixtures {

    fun paymentRepository(): PaymentRepository = getBean(PaymentRepository::class.java)

    val malaiSortCode: String get() = MalaiSortCode.CANONICAL

    fun fromAccountNumber(): String = digitsFor(someAccountUid())
    fun toAccountNumber(): String = digitsFor(anotherAccountUid())

    private fun digitsFor(uuid: UUID): String =
        "%08d".format(Math.floorMod(uuid.mostSignificantBits, 100_000_000L))

    fun ownAccountsExistInLookup() = CallableAction {
        stubGetAccount(someAccountUid(), someAccountHolderUid())
        stubGetAccount(anotherAccountUid(), someAccountHolderUid())
        stubResolveAccount(malaiSortCode, fromAccountNumber(), someAccountUid(), someAccountHolderUid())
        stubResolveAccount(malaiSortCode, toAccountNumber(), anotherAccountUid(), someAccountHolderUid())
    }

    fun anotherCustomersAccountExists(
        accountNumber: String,
        accountId: UUID = UUID.randomUUID(),
        accountHolderId: UUID = anotherAccountHolderUid(),
    ) = CallableAction {
        stubResolveAccount(malaiSortCode, accountNumber, accountId, accountHolderId)
        stubGetAccount(accountId, accountHolderId)
    }

    fun theDestinationIsExternalAtSortCode(accountNumber: String, sortCode: String = malaiSortCode) = CallableAction {
        stubResolveAccountReturnsNotFound(sortCode, accountNumber)
    }

    fun theAccountDoesNotBelongToTheHolder(accountId: UUID, accountHolderId: UUID) = CallableAction {
        stubGetAccountReturnsNotFound(accountId, accountHolderId)
    }

    private fun stubGetAccount(accountId: UUID, accountHolderId: UUID) {
        wiremock().stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                    "${WithAccountLookup.CONTEXT_PATH}/api/v1/internal/accounts/$accountId"
                )
            ).withQueryParam(
                "accountHolderId",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(accountHolderId.toString())
            ).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"accountId":"$accountId","accountHolderId":"$accountHolderId","currency":"GBP","sortCode":"65-82-37","accountNumber":"00000001","name":"Personal"}""")
            )
        )
    }

    private fun stubResolveAccount(sortCode: String, accountNumber: String, accountId: UUID, accountHolderId: UUID) {
        wiremock().stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                    "${WithAccountLookup.CONTEXT_PATH}/api/v1/internal/accounts/by-identifier"
                )
            ).withQueryParam(
                "sortCode",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(sortCode)
            ).withQueryParam(
                "accountNumber",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(accountNumber)
            ).willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("""{"accountId":"$accountId","accountHolderId":"$accountHolderId","currency":"GBP","sortCode":"65-82-37","accountNumber":"00000001","name":"Personal"}""")
            )
        )
    }

    private fun stubGetAccountReturnsNotFound(accountId: UUID, accountHolderId: UUID) {
        wiremock().stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                    "${WithAccountLookup.CONTEXT_PATH}/api/v1/internal/accounts/$accountId"
                )
            ).withQueryParam(
                "accountHolderId",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(accountHolderId.toString())
            ).willReturn(com.github.tomakehurst.wiremock.client.WireMock.notFound())
        )
    }

    private fun stubResolveAccountReturnsNotFound(sortCode: String, accountNumber: String) {
        wiremock().stubFor(
            com.github.tomakehurst.wiremock.client.WireMock.get(
                com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                    "${WithAccountLookup.CONTEXT_PATH}/api/v1/internal/accounts/by-identifier"
                )
            ).withQueryParam(
                "sortCode",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(sortCode)
            ).withQueryParam(
                "accountNumber",
                com.github.tomakehurst.wiremock.client.WireMock.equalTo(accountNumber)
            ).willReturn(com.github.tomakehurst.wiremock.client.WireMock.notFound())
        )
    }

    fun screeningModeIs(mode: ScreeningMode) = CallableAction {
        getBean(ScreeningProperties::class.java).mode = mode
    }

    fun thereIsNoIdempotencyKeyInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM idempotency_keys")
    }

    fun thereAreNoPaymentsInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM payments")
    }

    fun thereIsNoSqsMessageDedupInTheDatabase() = CallableAction {
        getBean(JdbcTemplate::class.java).update("DELETE FROM sqs_message_dedup")
    }

    fun theFpsSchemeIsReset() = CallableAction {
        CapturedFpsCommands.clear()
    }

    fun theSwiftSchemeIsReset() = CallableAction {
        CapturedSwiftCommands.clear()
    }

    fun theStubScheduleSettlesSwiftPayment(paymentId: UUID, endToEndId: UUID) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.SWIFT_EVENT_SETTLED)
        val settled = SwiftOutboundSettled(paymentId = paymentId, endToEndId = endToEndId)
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<SwiftOutboundSettled> { options ->
            options
                .queue(queueName)
                .payload(settled)
                .messageGroupId(paymentId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.SWIFT_EVENT_SETTLED,
            testingObjectMapper.writeValueAsString(settled),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to paymentId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            paymentRepository().findById(paymentId).orElseThrow().state shouldBe PaymentState.SETTLED
        }
    }

    fun theSubmittedSwiftOutbounds() = StateCollector { _ ->
        CapturedSwiftCommands.awaitForTestId(TestIdContext.value.value)
    }

    fun theStubScheduleSettlesPayment(paymentId: UUID, endToEndId: UUID) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_SETTLED)
        val settled = FpsOutboundSettled(paymentId = paymentId, endToEndId = endToEndId)
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<FpsOutboundSettled> { options ->
            options
                .queue(queueName)
                .payload(settled)
                .messageGroupId(paymentId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.FPS_EVENT_SETTLED,
            testingObjectMapper.writeValueAsString(settled),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to paymentId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            paymentRepository().findById(paymentId).orElseThrow().state shouldBe PaymentState.SETTLED
        }
    }

    fun theStubScheduleReturnsPayment(paymentId: UUID, endToEndId: UUID, reason: ReturnReason) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_RETURNED)
        val returned = FpsOutboundReturned(paymentId = paymentId, endToEndId = endToEndId, reason = reason)
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<FpsOutboundReturned> { options ->
            options
                .queue(queueName)
                .payload(returned)
                .messageGroupId(paymentId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.FPS_EVENT_RETURNED,
            testingObjectMapper.writeValueAsString(returned),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to paymentId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            paymentRepository().findById(paymentId).orElseThrow().state shouldBe PaymentState.RETURNED
        }
    }

    fun theInboundReturnPublisherIsReset() = CallableAction {
        CapturedFpsReturnCommands.clear()
    }

    fun theStubPublishesFpsInbound(
        endToEndId: UUID,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.FPS_EVENT_RECEIVED)
        val notify = FpsInboundNotify(
            endToEndId = endToEndId,
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = beneficiaryName,
            originatorSortCode = originatorSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<FpsInboundNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(endToEndId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.FPS_EVENT_RECEIVED,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to endToEndId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            getBean(SqsMessageDedupRepository::class.java)
                .existsById(SqsMessageDedupId(InboundPaymentProcessor.FPS_MESSAGE_TYPE, endToEndId)) shouldBe true
        }
    }

    fun theStubPublishesBacsInbound(
        endToEndId: UUID,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
        beneficiaryName: String,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.BACS_EVENT_RECEIVED)
        val notify = BacsInboundNotify(
            endToEndId = endToEndId,
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = beneficiaryName,
            originatorSortCode = originatorSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<BacsInboundNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(endToEndId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.BACS_EVENT_RECEIVED,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to endToEndId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            getBean(SqsMessageDedupRepository::class.java)
                .existsById(SqsMessageDedupId(InboundPaymentProcessor.BACS_MESSAGE_TYPE, endToEndId)) shouldBe true
        }
    }

    fun thePublishedInboundReturns() = StateCollector<List<FpsInboundReturned>> { _ ->
        CapturedFpsReturnCommands.awaitForTestId(TestIdContext.value.value)
    }

    fun theBacsCollectionReturnPublisherIsReset() = CallableAction {
        CapturedBacsCollectionReturns.clear()
    }

    fun theStubPublishesBacsCollection(
        endToEndId: UUID,
        debtorSortCode: String,
        debtorAccountNumber: String,
        debtorName: String,
        creditorSortCode: String,
        creditorAccountNumber: String,
        creditorName: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ) = CallableAction {
        val props = getBean(MalaiSqsMessagingProperties::class.java)
        val queueName = QueueNaming.physicalName(props.queueNamePrefix, PaymentsQueues.BACS_EVENT_COLLECTION)
        val notify = BacsCollectionNotify(
            endToEndId = endToEndId,
            debtorSortCode = debtorSortCode,
            debtorAccountNumber = debtorAccountNumber,
            debtorName = debtorName,
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
        )
        val testId = TestIdContext.value.value
        val dedupId = UUID.randomUUID().toString()
        getBean(SqsTemplate::class.java).send<BacsCollectionNotify> { options ->
            options
                .queue(queueName)
                .payload(notify)
                .messageGroupId(endToEndId.toString())
                .messageDeduplicationId(dedupId)
                .header(TestId.HEADER, testId)
        }
        SqsInteractionCapturer.captureEvent(
            TestIdContext.value,
            MalaiParty.PaymentsService,
            PaymentsQueues.BACS_EVENT_COLLECTION,
            testingObjectMapper.writeValueAsString(notify),
            headers = mapOf(
                TestId.HEADER to testId,
                "MessageGroupId" to endToEndId.toString(),
                "MessageDeduplicationId" to dedupId,
            ),
        )
        eventually {
            getBean(SqsMessageDedupRepository::class.java)
                .existsById(SqsMessageDedupId(DirectDebitCollectionProcessor.COLLECTION_MESSAGE_TYPE, endToEndId)) shouldBe true
        }
    }

    fun thePublishedCollectionReturns() = StateCollector<List<BacsCollectionReturn>> { _ ->
        CapturedBacsCollectionReturns.awaitForTestId(TestIdContext.value.value)
    }

    fun theLastSubmittedFpsOutbound() = StateCollector<FpsOutboundSubmit> { _ ->
        CapturedFpsCommands.awaitForTestId(TestIdContext.value.value).lastOrNull()
            ?: error("no FPS submit captured")
    }

    fun theSubmittedFpsOutbounds() = StateCollector { _ ->
        CapturedFpsCommands.awaitForTestId(TestIdContext.value.value)
    }

    fun theAuditEventRowsInTheDatabase() = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForList(
            """
            SELECT seq, action, actor_id, actor_type, resource_type, resource_id,
                   after_state::text AS after_state, trace_id
            FROM audit_events
            ORDER BY seq ASC
            """.trimIndent(),
        )
    }

    fun thePaymentInTheDatabase(paymentId: UUID) = StateCollector<PaymentEntity> { _ ->
        paymentRepository().findById(paymentId).orElseThrow {
            AssertionError("Payment with id $paymentId not found in database")
        }
    }

    fun thePaymentsInTheDatabase() = StateCollector { _ ->
        paymentRepository().findAll()
    }

    fun theIdempotencyKeyResponseBody(key: String, service: String = "payments-service") = StateCollector { _ ->
        getBean(JdbcTemplate::class.java).queryForObject(
            "SELECT encode(response_body, 'escape') FROM idempotency_keys WHERE service = ? AND key = ?",
            String::class.java,
            service,
            key,
        ) ?: ""
    }

    companion object {
        val SCHEME_SETTLEMENT_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010002")
        val FPS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010003")
        val BACS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID =
            UUID.fromString("00000000-0000-4000-8001-000000010005")
    }
}
