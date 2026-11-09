package com.malai.payments.service

import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.LedgerInsufficientFundsException
import com.malai.client.malai.ledger.model.LedgerEntryRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.client.malai.ledger.model.ReleaseHoldRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.exception.PaymentNotFoundException
import com.malai.payments.messages.BacsCollectionNotify
import com.malai.payments.messages.InboundNotify
import com.malai.payments.messages.ReturnReason
import com.malai.payments.repository.PaymentRepository
import com.malai.payments.scheme.producer.FpsCommandProducer
import com.malai.payments.scheme.producer.SwiftCommandProducer
import com.malai.commons.model.Currency
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PaymentLifecycleService(
    private val paymentRepository: PaymentRepository,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
    private val ledgerClient: LedgerClient,
    private val paymentsServiceTokenClient: PaymentsServiceTokenClient,
    private val fpsScheme: FpsCommandProducer,
    private val swiftScheme: SwiftCommandProducer,
    private val fxConversionService: FxConversionService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun transitionToSettled(paymentId: UUID) {
        val payment = paymentRepository.findById(paymentId).orElseThrow { PaymentNotFoundException() }

        if (payment.state == PaymentState.SETTLED) {
            log.info("payment already SETTLED, skipping transition: paymentId={}", paymentId)
            return
        }
        require(payment.state == PaymentState.PENDING) {
            "payment $paymentId cannot transition to SETTLED from ${payment.state}"
        }

        val now = timeService.now()
        settleViaHold(payment, now)

        paymentRepository.save(payment.copy(state = PaymentState.SETTLED))

        auditWriter.record(
            AuditEvent(
                actorId = payment.createdByAccountHolderId ?: payment.paymentId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_SETTLED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "state" to PaymentState.SETTLED.name,
                    "rail" to payment.rail.name,
                ),
                traceId = payment.traceId,
            ),
        )

        log.info(
            "payment settled: id={} rail={} amountMinor={} beneficiary={}",
            paymentId, payment.rail, payment.amountMinorUnits, payment.beneficiaryAccountId,
        )
    }

    @Transactional
    fun transitionToDeclined(paymentId: UUID) {
        val payment = paymentRepository.findById(paymentId).orElseThrow { PaymentNotFoundException() }
        if (!payment.isStillReferred("DECLINED")) return

        releaseViaHold(payment, timeService.now())

        paymentRepository.save(payment.copy(state = PaymentState.DECLINED))

        auditWriter.record(
            AuditEvent(
                actorId = payment.createdByAccountHolderId ?: payment.paymentId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_DECLINED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "state" to PaymentState.DECLINED.name,
                    "rail" to payment.rail.name,
                    "screeningDecisionId" to payment.screeningDecisionId.toString(),
                ),
                traceId = payment.traceId,
            ),
        )

        log.info(
            "payment declined: id={} rail={} decisionId={} amountMinor={} debtor={}",
            paymentId, payment.rail, payment.screeningDecisionId,
            payment.amountMinorUnits, payment.debtorAccountId,
        )
    }

    @Transactional
    fun transitionToReleased(paymentId: UUID) {
        val payment = paymentRepository.findById(paymentId).orElseThrow { PaymentNotFoundException() }
        if (!payment.isStillReferred("PENDING")) return

        val released = paymentRepository.save(payment.copy(state = PaymentState.PENDING))

        auditWriter.record(
            AuditEvent(
                actorId = payment.createdByAccountHolderId ?: payment.paymentId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_RELEASED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "state" to PaymentState.PENDING.name,
                    "rail" to payment.rail.name,
                    "screeningDecisionId" to payment.screeningDecisionId.toString(),
                ),
                traceId = payment.traceId,
            ),
        )

        dispatchToRail(released)

        log.info(
            "payment released to its rail: id={} rail={} decisionId={} amountMinor={} debtor={}",
            paymentId, released.rail, released.screeningDecisionId,
            released.amountMinorUnits, released.debtorAccountId,
        )
    }

    private fun dispatchToRail(payment: PaymentEntity) = when (payment.rail) {
        Rail.FPS -> fpsScheme.submit(payment)
        Rail.SWIFT -> swiftScheme.submit(payment, payment.fxTargetCurrency?.let {
            fxConversionService.convert(payment.currency, it, payment.amountMinorUnits)
        })
        else -> error("rail ${payment.rail} cannot be released from ${PaymentState.REFERRED}")
    }

    private fun PaymentEntity.isStillReferred(target: String): Boolean {
        if (state == PaymentState.REFERRED) return true
        log.info(
            "payment is no longer REFERRED, skipping transition to {}: paymentId={} state={}",
            target, paymentId, state,
        )
        return false
    }

    @Transactional
    fun transitionToReturned(paymentId: UUID, reason: ReturnReason) {
        val payment = paymentRepository.findById(paymentId).orElseThrow { PaymentNotFoundException() }

        if (payment.state == PaymentState.RETURNED) {
            log.info("payment already RETURNED, skipping transition: paymentId={}", paymentId)
            return
        }
        require(payment.state == PaymentState.PENDING) {
            "payment $paymentId cannot transition to RETURNED from ${payment.state}"
        }

        val now = timeService.now()
        releaseViaHold(payment, now)

        paymentRepository.save(payment.copy(state = PaymentState.RETURNED, returnReason = reason))

        auditWriter.record(
            AuditEvent(
                actorId = payment.createdByAccountHolderId ?: payment.paymentId,
                actorType = ActorType.SYSTEM,
                action = "PAYMENT_RETURNED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "state" to PaymentState.RETURNED.name,
                    "rail" to payment.rail.name,
                    "returnReason" to reason.name,
                ),
                traceId = payment.traceId,
            ),
        )

        log.info(
            "payment returned: id={} rail={} reason={} amountMinor={} debtor={}",
            paymentId, payment.rail, reason, payment.amountMinorUnits, payment.debtorAccountId,
        )
    }

    private fun settleViaHold(payment: PaymentEntity, now: Instant) {
        val token = paymentsServiceTokenClient.getToken()
        val initiatedById = payment.createdByAccountHolderId ?: payment.paymentId
        val debtorAccountId = requireNotNull(payment.debtorAccountId) {
            "PENDING outbound payment ${payment.paymentId} has no debtor_account_id; cannot settle"
        }

        ledgerClient.settleHold(
            token,
            payment.paymentId,
            PostTransactionRequest(
                transactionId = uuidGenerator.generate(),
                type = "PAYMENT_SETTLEMENT",
                initiatedById = initiatedById,
                postedAt = now.toString(),
                description = payment.description,
                traceId = payment.traceId,
                entries = listOf(
                    LedgerEntryRequest(
                        accountId = debtorAccountId,
                        amountMinorUnits = -payment.amountMinorUnits,
                        counterpartyId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        description = payment.description,
                    ),
                    LedgerEntryRequest(
                        accountId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                        amountMinorUnits = payment.amountMinorUnits,
                        counterpartyId = debtorAccountId,
                        description = payment.description,
                    ),
                ),
            ),
        )

        val beneficiaryAccountId = payment.beneficiaryAccountId
        if (beneficiaryAccountId != null) {
            ledgerClient.postTransaction(
                token,
                PostTransactionRequest(
                    transactionId = uuidGenerator.generate(),
                    type = "PAYMENT_CREDIT",
                    initiatedById = initiatedById,
                    postedAt = now.toString(),
                    description = payment.description,
                    traceId = payment.traceId,
                    entries = listOf(
                        LedgerEntryRequest(
                            accountId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                            amountMinorUnits = -payment.amountMinorUnits,
                            counterpartyId = beneficiaryAccountId,
                            description = payment.description,
                        ),
                        LedgerEntryRequest(
                            accountId = beneficiaryAccountId,
                            amountMinorUnits = payment.amountMinorUnits,
                            counterpartyId = SCHEME_SETTLEMENT_ACCOUNT_ID,
                            description = payment.description,
                        ),
                    ),
                ),
            )
        }
    }

    private fun releaseViaHold(payment: PaymentEntity, now: Instant) {
        ledgerClient.releaseHold(
            paymentsServiceTokenClient.getToken(),
            payment.paymentId,
            ReleaseHoldRequest(resolvedAt = now.toString()),
        )
    }

    @Transactional
    fun creditBeneficiary(
        message: InboundNotify,
        rail: Rail,
        beneficiaryAccountId: UUID,
        beneficiaryAccountHolderId: UUID,
    ) {
        val now = timeService.now()
        val paymentId = uuidGenerator.generate()
        val creditTransactionId = uuidGenerator.generate()
        val inboundSuspenseAccountId = inboundSuspenseAccountFor(rail)

        ledgerClient.postTransaction(
            paymentsServiceTokenClient.getToken(),
            PostTransactionRequest(
                transactionId = creditTransactionId,
                type = "PAYMENT_CREDIT",
                initiatedById = beneficiaryAccountHolderId,
                postedAt = now.toString(),
                description = message.description,
                traceId = null,
                entries = listOf(
                    LedgerEntryRequest(
                        accountId = inboundSuspenseAccountId,
                        amountMinorUnits = -message.amountMinorUnits,
                        counterpartyId = beneficiaryAccountId,
                        description = message.description,
                    ),
                    LedgerEntryRequest(
                        accountId = beneficiaryAccountId,
                        amountMinorUnits = message.amountMinorUnits,
                        counterpartyId = inboundSuspenseAccountId,
                        description = message.description,
                    ),
                ),
            ),
        )

        paymentRepository.save(
            PaymentEntity(
                paymentId = paymentId,
                rail = rail,
                direction = PaymentDirection.INBOUND,
                state = PaymentState.SETTLED,
                debtorAccountId = null,
                debtorSortCode = message.originatorSortCode,
                debtorAccountNo = message.originatorAccountNumber,
                debtorName = message.originatorName,
                beneficiaryAccountId = beneficiaryAccountId,
                beneficiarySortCode = message.beneficiarySortCode,
                beneficiaryAccountNo = message.beneficiaryAccountNumber,
                beneficiaryName = message.beneficiaryName,
                amountMinorUnits = message.amountMinorUnits,
                currency = Currency.valueOf(message.currency),
                description = message.description,
                endToEndId = message.endToEndId,
                createdAt = now,
                createdByAccountHolderId = beneficiaryAccountHolderId,
                traceId = null,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = beneficiaryAccountHolderId,
                actorType = ActorType.SYSTEM,
                action = "INBOUND_PAYMENT_RECEIVED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "state" to PaymentState.SETTLED.name,
                    "rail" to rail.name,
                    "direction" to PaymentDirection.INBOUND.name,
                    "beneficiaryAccountId" to beneficiaryAccountId.toString(),
                    "amountMinorUnits" to message.amountMinorUnits,
                    "currency" to message.currency,
                ),
                traceId = null,
            ),
        )

        log.info(
            "inbound payment received: id={} rail={} beneficiary={} amountMinor={} endToEndId={}",
            paymentId, rail, beneficiaryAccountId, message.amountMinorUnits, message.endToEndId,
        )
    }

    @Transactional
    fun collectFromDebtor(
        message: BacsCollectionNotify,
        debtorAccountId: UUID,
        debtorAccountHolderId: UUID,
    ): Boolean {
        val now = timeService.now()
        val paymentId = uuidGenerator.generate()
        val debitTransactionId = uuidGenerator.generate()
        val suspenseAccountId = BACS_INBOUND_SUSPENSE_ACCOUNT_ID

        val settled = try {
            ledgerClient.postTransaction(
                paymentsServiceTokenClient.getToken(),
                PostTransactionRequest(
                    transactionId = debitTransactionId,
                    type = "DIRECT_DEBIT_COLLECTION",
                    initiatedById = debtorAccountHolderId,
                    postedAt = now.toString(),
                    description = message.description,
                    traceId = null,
                    entries = listOf(
                        LedgerEntryRequest(
                            accountId = debtorAccountId,
                            amountMinorUnits = -message.amountMinorUnits,
                            counterpartyId = suspenseAccountId,
                            description = message.description,
                        ),
                        LedgerEntryRequest(
                            accountId = suspenseAccountId,
                            amountMinorUnits = message.amountMinorUnits,
                            counterpartyId = debtorAccountId,
                            description = message.description,
                        ),
                    ),
                ),
            )
            true
        } catch (ex: LedgerInsufficientFundsException) {
            log.info(
                "bacs collection returned, insufficient funds: endToEndId={} debtor={} amountMinor={}",
                message.endToEndId, debtorAccountId, message.amountMinorUnits,
            )
            false
        }

        paymentRepository.save(
            PaymentEntity(
                paymentId = paymentId,
                rail = Rail.BACS_DIRECT_DEBIT,
                direction = PaymentDirection.INBOUND,
                state = if (settled) PaymentState.SETTLED else PaymentState.RETURNED,
                returnReason = if (settled) null else ReturnReason.INSUFFICIENT_FUNDS,
                debtorAccountId = debtorAccountId,
                debtorSortCode = message.debtorSortCode,
                debtorAccountNo = message.debtorAccountNumber,
                debtorName = message.debtorName,
                beneficiaryAccountId = null,
                beneficiarySortCode = message.creditorSortCode,
                beneficiaryAccountNo = message.creditorAccountNumber,
                beneficiaryName = message.creditorName,
                amountMinorUnits = message.amountMinorUnits,
                currency = Currency.valueOf(message.currency),
                description = message.description,
                endToEndId = message.endToEndId,
                createdAt = now,
                createdByAccountHolderId = debtorAccountHolderId,
                traceId = null,
            ),
        )

        auditWriter.record(
            AuditEvent(
                actorId = debtorAccountHolderId,
                actorType = ActorType.SYSTEM,
                action = if (settled) "INBOUND_COLLECTION_SETTLED" else "INBOUND_COLLECTION_RETURNED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = buildMap {
                    put("state", (if (settled) PaymentState.SETTLED else PaymentState.RETURNED).name)
                    put("rail", Rail.BACS_DIRECT_DEBIT.name)
                    put("direction", PaymentDirection.INBOUND.name)
                    put("debtorAccountId", debtorAccountId.toString())
                    put("amountMinorUnits", message.amountMinorUnits)
                    put("currency", message.currency)
                    if (!settled) put("returnReason", ReturnReason.INSUFFICIENT_FUNDS.name)
                },
                traceId = null,
            ),
        )

        log.info(
            "bacs collection processed: id={} settled={} debtor={} amountMinor={} endToEndId={}",
            paymentId, settled, debtorAccountId, message.amountMinorUnits, message.endToEndId,
        )

        return settled
    }

    private fun inboundSuspenseAccountFor(rail: Rail): UUID = when (rail) {
        Rail.FPS -> FPS_INBOUND_SUSPENSE_ACCOUNT_ID
        Rail.BACS_DIRECT_CREDIT -> BACS_INBOUND_SUSPENSE_ACCOUNT_ID
        else -> error("rail $rail has no inbound suspense account")
    }

    companion object {
        val SCHEME_SETTLEMENT_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8001-000000010002")
        val FPS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8001-000000010003")
        val BACS_INBOUND_SUSPENSE_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-8001-000000010005")
    }
}
