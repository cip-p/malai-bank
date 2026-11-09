package com.malai.accounts.service

import tools.jackson.databind.ObjectMapper
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.accounts.exception.AccountNotFoundException
import com.malai.accounts.model.TransferRequest
import com.malai.accounts.model.TransferResponse
import com.malai.accounts.repository.AccountIdentifierRepository
import com.malai.accounts.repository.AccountRepository
import com.malai.client.malai.MalaiClientResponseException
import com.malai.client.malai.payments.MalaiPaymentsClient
import com.malai.client.malai.payments.model.InitiatePaymentRequest
import com.malai.client.malai.payments.model.InitiatePaymentResponse
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode
import com.malai.commons.exception.MalaiCode.PAYMENT_AMOUNT_INVALID
import com.malai.commons.exception.MalaiCode.PAYMENT_DESTINATION_NOT_FOUND
import com.malai.commons.exception.MalaiCode.PAYMENT_INSUFFICIENT_FUNDS
import com.malai.commons.exception.MalaiCode.PAYMENT_TO_SAME_ACCOUNT
import com.malai.commons.exception.MalaiCode.TRANSFER_AMOUNT_INVALID
import com.malai.commons.exception.MalaiCode.TRANSFER_DESTINATION_NOT_FOUND
import com.malai.commons.exception.MalaiCode.TRANSFER_INSUFFICIENT_FUNDS
import com.malai.commons.exception.MalaiCode.TRANSFER_TO_SAME_ACCOUNT
import com.malai.commons.model.Currency
import com.malai.security.MalaiUserWithAccountHolderId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.*

@Service
class TransfersService(
    private val accountRepository: AccountRepository,
    private val accountIdentifierRepository: AccountIdentifierRepository,
    private val paymentsClient: MalaiPaymentsClient,
    private val objectMapper: ObjectMapper,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun executeTransfer(
        user: MalaiUserWithAccountHolderId,
        fromAccountId: UUID,
        accessToken: String,
        idempotencyKey: String,
        request: TransferRequest,
    ): TransferResponse {
        requireOwnDestination(user, request)

        val initiatePaymentRequest = InitiatePaymentRequest(
            fromAccountId = fromAccountId,
            toSortCode = request.toSortCode,
            toAccountNumber = request.toAccountNumber,
            amountMinorUnits = request.amountMinorUnits,
            currency = request.currency,
            description = request.description,
        )

        val response = try {
            paymentsClient.initiatePayment(accessToken, user.accountHolderId, idempotencyKey, initiatePaymentRequest)
        } catch (ex: MalaiClientResponseException) {
            throw translateUpstreamError(ex)
        }

        log.info(
            "transfer delegated to payments-service: paymentId={} from={} to={} amountMinor={} {}",
            response.paymentId, response.fromAccountId, response.toAccountId,
            response.amountMinorUnits, response.currency,
        )

        return response.toTransferResponse()
    }

    private fun requireOwnDestination(user: MalaiUserWithAccountHolderId, request: TransferRequest) {
        val identifier = accountIdentifierRepository
            .findByIdentifierTypeAndBankIdentifierAndAccountIdentifier(
                identifierType = SORT_CODE,
                bankIdentifier = request.toSortCode,
                accountIdentifier = request.toAccountNumber,
            ) ?: throw BadRequestException(TRANSFER_DESTINATION_NOT_FOUND)
        val to = accountRepository.findById(identifier.accountId).orElse(null)
            ?: throw BadRequestException(TRANSFER_DESTINATION_NOT_FOUND)
        if (to.accountHolderId != user.accountHolderId) {
            throw BadRequestException(TRANSFER_DESTINATION_NOT_FOUND)
        }
    }

    private fun translateUpstreamError(ex: MalaiClientResponseException): RuntimeException {
        if (ex.statusCode == 404) {
            return AccountNotFoundException()
        }
        val code = parseMalaiCode(ex.responseBody)
        return when (code) {
            PAYMENT_INSUFFICIENT_FUNDS -> BadRequestException(TRANSFER_INSUFFICIENT_FUNDS)
            PAYMENT_DESTINATION_NOT_FOUND -> BadRequestException(TRANSFER_DESTINATION_NOT_FOUND)
            PAYMENT_AMOUNT_INVALID -> BadRequestException(TRANSFER_AMOUNT_INVALID)
            PAYMENT_TO_SAME_ACCOUNT -> BadRequestException(TRANSFER_TO_SAME_ACCOUNT)
            else -> ex
        }
    }

    private fun parseMalaiCode(body: String?): MalaiCode? {
        if (body.isNullOrBlank()) return null
        val node = runCatching { objectMapper.readTree(body) }.getOrNull() ?: return null
        val message = node.get("message")?.asString() ?: return null
        val numeric = message.toIntOrNull() ?: return null
        return MalaiCode.entries.firstOrNull { it.code == numeric }
    }

    private fun InitiatePaymentResponse.toTransferResponse(): TransferResponse =
        TransferResponse(
            transactionId = paymentId,
            postedAt = Instant.parse(createdAt),
            fromAccountId = fromAccountId
                ?: error("payments-service returned an OUTBOUND payment without fromAccountId"),
            toAccountId = toAccountId
                ?: error("payments-service routed a /transfers call without a beneficiary account"),
            amountMinorUnits = amountMinorUnits,
            currency = Currency.valueOf(currency),
        )
}
