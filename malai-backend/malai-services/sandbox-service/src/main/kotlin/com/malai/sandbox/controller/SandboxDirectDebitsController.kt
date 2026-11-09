package com.malai.sandbox.controller

import com.malai.client.malai.accounts.lookup.AccountLookupClient
import com.malai.client.malai.directdebit.DirectDebitLodgementClient
import com.malai.client.malai.directdebit.model.LodgeMandateRequest
import com.malai.client.malai.directdebit.model.LodgedMandateResponse
import com.malai.client.malai.stub.MalaiStubClient
import com.malai.commons.exception.BadRequestException
import com.malai.commons.exception.MalaiCode.ACCOUNT_NOT_FOUND
import com.malai.commons.exception.MalaiCode.CREDITOR_NOT_FOUND
import com.malai.commons.exception.MalaiCode.GENERIC_BAD_REQUEST
import com.malai.commons.exception.NotFoundException
import com.malai.commons.model.Frequency
import com.malai.sandbox.service.SandboxServiceTokenClient
import com.malai.security.MalaiUser
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.util.UUID

/**
 * Sandbox mandate lodgement (SO/DD lifecycle issue 09). A demo driver picks a fictional creditor
 * from the counterparty directory and one of the active holder's accounts; the mandate is written
 * bank-side through the internal direct-debit endpoint, the way a real creditor would lodge one —
 * there is no customer-facing "create direct debit" surface.
 *
 * Ownership is enforced by resolving the account against the active holder (a not-owned account
 * reads as `404`, never `403`, so ids can't be enumerated — same pattern as inbound-payments); the
 * creditor's name is snapshotted from the directory's `nameOnRecord`. No `Idempotency-Key`, matching
 * the other sandbox writes.
 */
@RestController
@RequestMapping("/api/v1/sandbox/direct-debits")
class SandboxDirectDebitsController(
    private val stubClient: MalaiStubClient,
    private val accountLookupClient: AccountLookupClient,
    private val directDebitLodgementClient: DirectDebitLodgementClient,
    private val sandboxServiceTokenClient: SandboxServiceTokenClient,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(CREATED)
    fun lodge(
        @AuthenticationPrincipal user: MalaiUser,
        @RequestBody request: SandboxDirectDebitRequest,
    ): LodgedMandateResponse {
        val holder = user as? MalaiUserWithAccountHolderId
            ?: throw BadRequestException(GENERIC_BAD_REQUEST, "MAL-Account-Holder-Id header is required")

        request.requireValid()

        val serviceToken = sandboxServiceTokenClient.getToken()

        val account = accountLookupClient.getAccount(serviceToken, request.accountId, holder.accountHolderId)
            ?: throw NotFoundException(ACCOUNT_NOT_FOUND)

        val creditor = stubClient.getCounterparties().counterparties.firstOrNull {
            it.sortCode == request.creditorSortCode && it.accountNumber == request.creditorAccountNumber
        } ?: throw NotFoundException(CREDITOR_NOT_FOUND)

        return directDebitLodgementClient.lodge(
            serviceToken,
            LodgeMandateRequest(
                accountId = account.accountId,
                creditorName = creditor.nameOnRecord,
                creditorSortCode = creditor.sortCode,
                creditorAccountNumber = creditor.accountNumber,
                reference = request.reference,
                amountMinorUnits = request.amountMinorUnits!!,
                currency = account.currency,
                frequency = request.frequency!!.name,
                startDate = request.startDate!!.toString(),
            ),
        )
    }
}

data class SandboxDirectDebitRequest(
    val accountId: UUID,
    val creditorSortCode: String?,
    val creditorAccountNumber: String?,
    val amountMinorUnits: Long?,
    val reference: String? = null,
    val frequency: Frequency?,
    val startDate: LocalDate?,
) {
    fun requireValid() {
        if (creditorSortCode.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "creditorSortCode is required")
        if (creditorAccountNumber.isNullOrBlank()) throw BadRequestException(GENERIC_BAD_REQUEST, "creditorAccountNumber is required")
        if (amountMinorUnits == null || amountMinorUnits <= 0) throw BadRequestException(GENERIC_BAD_REQUEST, "amountMinorUnits must be > 0")
        if (frequency == null) throw BadRequestException(GENERIC_BAD_REQUEST, "frequency is required")
        if (startDate == null) throw BadRequestException(GENERIC_BAD_REQUEST, "startDate is required")
    }
}
