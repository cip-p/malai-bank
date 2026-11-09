package com.malai.accounts.controller

import com.malai.accounts.model.TransferRequest
import com.malai.accounts.model.TransferResponse
import com.malai.accounts.service.TransfersService
import com.malai.security.AuthCookies
import com.malai.security.MalaiUserWithAccountHolderId
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/accounts")
class TransfersController(
    private val service: TransfersService,
    private val authCookies: AuthCookies,
) {

    @PostMapping("/{accountId}/transfers", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun transfer(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @PathVariable accountId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @Valid @RequestBody request: TransferRequest,
        httpRequest: HttpServletRequest,
    ): TransferResponse =
        service.executeTransfer(
            user = user,
            fromAccountId = accountId,
            accessToken = authCookies.readAccessToken(httpRequest),
            idempotencyKey = idempotencyKey,
            request = request,
        )
}
