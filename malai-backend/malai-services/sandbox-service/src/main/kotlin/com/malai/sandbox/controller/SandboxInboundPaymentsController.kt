package com.malai.sandbox.controller

import com.malai.client.malai.accounts.lookup.AccountLookupClient
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.inbound.model.InboundPaymentForwardPayload
import com.malai.client.malai.stub.inbound.model.SandboxRail
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.ACCOUNT_NOT_FOUND
import com.malai.commons.exception.MalaiCode.GENERIC_BAD_REQUEST
import com.malai.commons.exception.NotFoundException
import com.malai.sandbox.service.SandboxServiceTokenClient
import com.malai.security.MalaiUser
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/sandbox/inbound-payments")
class SandboxInboundPaymentsController(
    private val stubClient: MalaiStubClient,
    private val accountLookupClient: AccountLookupClient,
    private val sandboxServiceTokenClient: SandboxServiceTokenClient,
) {

    @PostMapping(consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(ACCEPTED)
    fun trigger(
        @AuthenticationPrincipal user: MalaiUser,
        @RequestBody request: SandboxInboundPaymentRequest,
    ) {
        val holder = user as? MalaiUserWithAccountHolderId
            ?: throw BadRequestException(GENERIC_BAD_REQUEST, "MAL-Account-Holder-Id header is required")

        val rail = parseRail(request.rail)
        request.requireValid()

        val credited = accountLookupClient.getAccount(
            sandboxServiceTokenClient.getToken(),
            request.creditedAccountId,
            holder.accountHolderId,
        ) ?: throw NotFoundException(ACCOUNT_NOT_FOUND)

        stubClient.postInbound(
            rail,
            InboundPaymentForwardPayload(
                beneficiarySortCode = credited.sortCode,
                beneficiaryAccountNumber = credited.accountNumber,
                beneficiaryName = credited.name,
                originatorSortCode = request.originatorSortCode!!,
                originatorAccountNumber = request.originatorAccountNumber!!,
                originatorName = request.originatorName!!,
                amountMinorUnits = request.amountMinorUnits!!,
                currency = credited.currency,
                description = request.reference,
            ),
        )
    }

    private fun parseRail(raw: String?): SandboxRail {
        if (raw == null) throw BadRequestException(GENERIC_BAD_REQUEST, "rail is required")
        return when (raw) {
            "FPS" -> SandboxRail.FPS
            "BACS" -> SandboxRail.BACS
            else -> throw BadRequestException(GENERIC_BAD_REQUEST, "rail must be FPS or BACS")
        }
    }
}

data class SandboxInboundPaymentRequest(
    val rail: String?,
    val creditedAccountId: UUID,
    val originatorSortCode: String?,
    val originatorAccountNumber: String?,
    val originatorName: String?,
    val amountMinorUnits: Long?,
    val reference: String? = null,
) {
    fun requireValid() {
        if (originatorSortCode.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "originatorSortCode is required")
        if (originatorAccountNumber.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "originatorAccountNumber is required")
        if (originatorName.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "originatorName is required")
        if (amountMinorUnits == null || amountMinorUnits <= 0) throw BadRequestException(GENERIC_BAD_REQUEST, "amountMinorUnits must be > 0")
    }
}
