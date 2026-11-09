package com.malai.payments.service

import com.malai.client.malai.accounts.lookup.AccountLookupClient
import com.malai.client.malai.accounts.lookup.model.InternalAccountResponse
import com.malai.client.malai.ledger.LedgerClient
import com.malai.client.malai.ledger.LedgerInsufficientFundsException
import com.malai.client.malai.ledger.model.LedgerEntryRequest
import com.malai.client.malai.ledger.model.PlaceHoldRequest
import com.malai.client.malai.ledger.model.PostTransactionRequest
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.PAYMENT_AMOUNT_INVALID
import com.malai.commons.exception.MalaiCode.PAYMENT_DESTINATION_NOT_FOUND
import com.malai.commons.exception.MalaiCode.PAYMENT_IDENTIFIER_MIXED
import com.malai.commons.exception.MalaiCode.PAYMENT_INSUFFICIENT_FUNDS
import com.malai.commons.exception.MalaiCode.PAYMENT_TO_SAME_ACCOUNT
import com.malai.commons.model.Currency
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.exception.AccountNotFoundException
import com.malai.payments.exception.PaymentNotFoundException
import com.malai.payments.fx.FxConversion
import com.malai.payments.model.InternalPaymentRequest
import com.malai.payments.model.PaymentRequest
import com.malai.payments.model.PaymentResponse
import com.malai.payments.repository.PaymentRepository
import com.malai.payments.scheme.producer.FpsCommandProducer
import com.malai.payments.scheme.producer.SwiftCommandProducer
import com.malai.payments.screening.PaymentScreener
import com.malai.payments.screening.ScreeningMode
import com.malai.payments.screening.ScreeningProperties
import com.malai.payments.screening.ScreeningSubject
import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class PaymentsService(
    private val paymentRepository: PaymentRepository,
    private val accountLookupClient: AccountLookupClient,
    private val auditWriter: AuditWriter,
    private val timeService: TimeService,
    private val uuidGenerator: UuidGenerator,
    private val fpsScheme: FpsCommandProducer,
    private val swiftScheme: SwiftCommandProducer,
    private val fxConversionService: FxConversionService,
    private val ledgerClient: LedgerClient,
    private val paymentsServiceTokenClient: PaymentsServiceTokenClient,
    private val paymentScreener: PaymentScreener,
    private val screeningProperties: ScreeningProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun initiatePayment(
        user: MalaiUserWithAccountHolderId,
        request: PaymentRequest,
    ): PaymentResponse =
        initiate(
            request = request,
            accountHolderId = user.accountHolderId,
            actorId = user.userId,
            actorType = ActorType.CUSTOMER,
            endToEndId = null,
        )

    @Transactional
    fun initiateScheduledPayment(request: InternalPaymentRequest): PaymentResponse {
        val endToEndId = request.endToEndId
            ?: throw BadRequestException(PAYMENT_DESTINATION_NOT_FOUND)
        paymentRepository.findByEndToEndId(endToEndId)?.let { existing ->
            log.info(
                "scheduled payment bounced on existing end-to-end id: endToEndId={} paymentId={}",
                endToEndId, existing.paymentId,
            )
            return existing.toResponse(null)
        }
        val accountHolderId = request.accountHolderId
            ?: throw BadRequestException(PAYMENT_DESTINATION_NOT_FOUND)
        return initiate(
            request = PaymentRequest(
                fromAccountId = request.fromAccountId,
                toSortCode = request.toSortCode,
                toAccountNumber = request.toAccountNumber,
                toName = request.toName,
                amountMinorUnits = request.amountMinorUnits,
                currency = request.currency,
                description = request.reference,
            ),
            accountHolderId = accountHolderId,
            actorId = accountHolderId,
            actorType = ActorType.SYSTEM,
            endToEndId = endToEndId,
        )
    }

    private fun initiate(
        request: PaymentRequest,
        accountHolderId: UUID,
        actorId: UUID,
        actorType: ActorType,
        endToEndId: UUID?,
    ): PaymentResponse {
        val fromAccountId = request.fromAccountId
            ?: throw BadRequestException(PAYMENT_DESTINATION_NOT_FOUND)

        val destinationKind = classifyDestination(request)

        val from = accountLookupClient.getAccount(paymentsServiceTokenClient.getToken(), fromAccountId, accountHolderId)
            ?: throw AccountNotFoundException()
        val fromCurrency = Currency.valueOf(from.currency)

        val currency = parseCurrency(request.currency)

        if (destinationKind == DestinationKind.UK && currency != fromCurrency) {
            throw BadRequestException(PAYMENT_AMOUNT_INVALID)
        }
        if (destinationKind == DestinationKind.INTERNATIONAL
            && currency != fromCurrency
            && !fxConversionService.supports(fromCurrency, currency)
        ) {
            throw BadRequestException(PAYMENT_AMOUNT_INVALID)
        }

        val to = if (destinationKind == DestinationKind.UK) resolveDestination(request) else null

        if (to != null && to.accountId == from.accountId) {
            throw BadRequestException(PAYMENT_TO_SAME_ACCOUNT)
        }

        val rail = when (destinationKind) {
            DestinationKind.INTERNATIONAL -> Rail.SWIFT
            DestinationKind.UK -> when {
                to == null -> Rail.FPS
                to.accountHolderId == from.accountHolderId -> Rail.BOOK_TRANSFER
                else -> Rail.FPS
            }
        }

        val fxConversion: FxConversion? = if (rail == Rail.SWIFT && currency != fromCurrency) {
            fxConversionService.convert(fromCurrency, currency, request.amountMinorUnits)
        } else {
            null
        }

        val paymentId = uuidGenerator.generate()
        val resolvedEndToEndId = endToEndId ?: uuidGenerator.generate()
        val now = timeService.now()
        val traceId = MDC.get(MDC_TRACE_ID)

        when (rail) {
            Rail.BOOK_TRANSFER ->
                postBookTransfer(uuidGenerator.generate(), from, to!!, request, now, traceId, accountHolderId)
            Rail.FPS, Rail.SWIFT ->
                placeOutboundHold(paymentId, from, request, now)
            else -> error("rail $rail unreachable for this slice")
        }

        val initialState = if (rail == Rail.BOOK_TRANSFER) PaymentState.SETTLED else PaymentState.PENDING

        val initiated = paymentRepository.save(
            PaymentEntity(
                paymentId = paymentId,
                rail = rail,
                direction = PaymentDirection.OUTBOUND,
                state = initialState,
                debtorAccountId = from.accountId,
                beneficiaryAccountId = to?.accountId,
                beneficiarySortCode = request.toSortCode,
                beneficiaryAccountNo = request.toAccountNumber,
                beneficiaryName = request.toName,
                beneficiaryIban = request.toIban,
                beneficiaryBic = request.toBic,
                paymentPurpose = request.paymentPurpose,
                amountMinorUnits = request.amountMinorUnits,
                currency = if (rail == Rail.SWIFT) fromCurrency else currency,
                fxTargetCurrency = fxConversion?.currency,
                description = request.description,
                endToEndId = resolvedEndToEndId,
                copVerdictOverride = request.copVerdictOverride,
                createdAt = now,
                createdByAccountHolderId = accountHolderId,
                traceId = traceId,
            ),
        )

        val screening = if (isScreened(actorType, rail)) {
            paymentScreener.screen(ScreeningSubject.of(initiated, from), paymentsServiceTokenClient.getToken())
        } else {
            null
        }

        val referral = screening?.takeIf { screeningProperties.mode == ScreeningMode.ENFORCE && it.flagged }

        val payment = if (referral == null) {
            initiated
        } else {
            paymentRepository.save(
                initiated.copy(
                    state = PaymentState.REFERRED,
                    screeningDecisionId = referral.decisionId,
                ),
            )
        }

        auditWriter.record(
            AuditEvent(
                actorId = actorId,
                actorType = actorType,
                action = "PAYMENT_INITIATED",
                resourceType = "Payment",
                resourceId = paymentId.toString(),
                afterState = mapOf(
                    "rail" to payment.rail.name,
                    "state" to payment.state.name,
                    "fromAccountId" to from.accountId.toString(),
                    "toAccountId" to (to?.accountId?.toString() ?: ""),
                    "toSortCode" to (request.toSortCode ?: ""),
                    "toAccountNumber" to (request.toAccountNumber ?: ""),
                    "toIban" to (request.toIban ?: ""),
                    "toBic" to (request.toBic ?: ""),
                    "amountMinorUnits" to request.amountMinorUnits,
                    "currency" to currency.name,
                ),
                traceId = traceId,
            ),
        )

        if (referral != null) {
            auditWriter.record(
                AuditEvent(
                    actorId = accountHolderId,
                    actorType = ActorType.SYSTEM,
                    action = "PAYMENT_REFERRED",
                    resourceType = "Payment",
                    resourceId = paymentId.toString(),
                    afterState = mapOf(
                        "state" to PaymentState.REFERRED.name,
                        "rail" to payment.rail.name,
                        "screeningDecisionId" to referral.decisionId.toString(),
                    ),
                    traceId = traceId,
                ),
            )

            log.info(
                "payment referred by screening: id={} rail={} decisionId={} amountMinor={} {}",
                paymentId, payment.rail, referral.decisionId, request.amountMinorUnits, currency.name,
            )

            return payment.toResponse(fxConversion)
        }

        when (rail) {
            Rail.FPS -> fpsScheme.submit(payment)
            Rail.SWIFT -> swiftScheme.submit(payment, fxConversion)
            else -> {}
        }

        log.info(
            "payment initiated: id={} rail={} state={} from={} to={} amountMinor={} {}",
            paymentId, payment.rail, payment.state, from.accountId, to?.accountId,
            request.amountMinorUnits, currency.name,
        )

        return payment.toResponse(fxConversion)
    }

    private fun isScreened(actorType: ActorType, rail: Rail): Boolean =
        actorType == ActorType.CUSTOMER && (rail == Rail.FPS || rail == Rail.SWIFT)

    private fun postBookTransfer(
        transactionId: UUID,
        from: InternalAccountResponse,
        to: InternalAccountResponse,
        request: PaymentRequest,
        now: java.time.Instant,
        traceId: String?,
        initiatedById: UUID,
    ) {
        try {
            ledgerClient.postTransaction(
                paymentsServiceTokenClient.getToken(),
                PostTransactionRequest(
                    transactionId = transactionId,
                    type = "PAYMENT",
                    initiatedById = initiatedById,
                    postedAt = now.toString(),
                    description = request.description,
                    traceId = traceId,
                    entries = listOf(
                        LedgerEntryRequest(
                            accountId = from.accountId,
                            amountMinorUnits = -request.amountMinorUnits,
                            counterpartyId = to.accountId,
                            description = request.description,
                        ),
                        LedgerEntryRequest(
                            accountId = to.accountId,
                            amountMinorUnits = request.amountMinorUnits,
                            counterpartyId = from.accountId,
                            description = request.description,
                        ),
                    ),
                ),
            )
        } catch (ex: LedgerInsufficientFundsException) {
            throw BadRequestException(PAYMENT_INSUFFICIENT_FUNDS)
        }
    }

    private fun placeOutboundHold(
        paymentId: UUID,
        from: InternalAccountResponse,
        request: PaymentRequest,
        now: Instant,
    ) {
        try {
            ledgerClient.placeHold(
                paymentsServiceTokenClient.getToken(),
                PlaceHoldRequest(
                    holdId = paymentId,
                    accountId = from.accountId,
                    amountMinorUnits = request.amountMinorUnits,
                    placedAt = now.toString(),
                ),
            )
        } catch (ex: LedgerInsufficientFundsException) {
            throw BadRequestException(PAYMENT_INSUFFICIENT_FUNDS)
        }
    }

    fun getPayment(
        user: MalaiUserWithAccountHolderId,
        paymentId: UUID,
    ): PaymentResponse {
        val payment = paymentRepository.findByPaymentIdAndCreatedByAccountHolderId(
            paymentId,
            user.accountHolderId,
        ) ?: throw PaymentNotFoundException()
        return payment.toResponse(null)
    }

    private fun PaymentEntity.toResponse(fxConversion: FxConversion?): PaymentResponse =
        PaymentResponse(
            paymentId = paymentId,
            state = state,
            rail = rail,
            direction = direction,
            createdAt = createdAt,
            endToEndId = endToEndId,
            fromAccountId = debtorAccountId,
            fromSortCode = debtorSortCode?.takeIf { direction == PaymentDirection.INBOUND },
            fromAccountNumber = debtorAccountNo?.takeIf { direction == PaymentDirection.INBOUND },
            fromName = debtorName?.takeIf { direction == PaymentDirection.INBOUND },
            toAccountId = beneficiaryAccountId,
            toSortCode = beneficiarySortCode,
            toAccountNumber = beneficiaryAccountNo,
            toName = beneficiaryName,
            toIban = beneficiaryIban,
            toBic = beneficiaryBic,
            paymentPurpose = paymentPurpose,
            amountMinorUnits = amountMinorUnits,
            currency = currency,
            description = description,
            returnReason = returnReason,
            fxConversion = fxConversion,
        )

    private fun classifyDestination(request: PaymentRequest): DestinationKind {
        val hasUk = !request.toSortCode.isNullOrBlank() || !request.toAccountNumber.isNullOrBlank()
        val hasIntl = !request.toIban.isNullOrBlank() || !request.toBic.isNullOrBlank()
        if (hasUk && hasIntl) {
            throw BadRequestException(PAYMENT_IDENTIFIER_MIXED)
        }
        if (hasIntl) {
            if (request.toIban.isNullOrBlank() || request.toBic.isNullOrBlank()) {
                throw BadRequestException(PAYMENT_DESTINATION_NOT_FOUND)
            }
            return DestinationKind.INTERNATIONAL
        }
        if (request.toSortCode.isNullOrBlank() || request.toAccountNumber.isNullOrBlank()) {
            throw BadRequestException(PAYMENT_DESTINATION_NOT_FOUND)
        }
        return DestinationKind.UK
    }

    private fun resolveDestination(request: PaymentRequest): InternalAccountResponse? {
        val sortCode = request.toSortCode ?: return null
        val accountNumber = request.toAccountNumber ?: return null
        return accountLookupClient.resolveBySortCodeAndAccountNumber(
            paymentsServiceTokenClient.getToken(),
            sortCode,
            accountNumber,
        )
    }

    private fun parseCurrency(raw: String): Currency =
        runCatching { Currency.valueOf(raw) }
            .getOrElse { throw BadRequestException(PAYMENT_AMOUNT_INVALID) }

    private enum class DestinationKind { UK, INTERNATIONAL }

    companion object {
        private const val MDC_TRACE_ID = "trace_id"
    }
}
