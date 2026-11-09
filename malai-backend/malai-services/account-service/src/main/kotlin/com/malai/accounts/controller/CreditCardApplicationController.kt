package com.malai.accounts.controller

import com.malai.accounts.exception.AccountNotFoundException
import com.malai.accounts.model.CreditCardApplicationResponse
import com.malai.accounts.service.AccountsService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/credit-card-applications")
class CreditCardApplicationController(
    private val accountsService: AccountsService,
) {

    @PostMapping("/{accountHolderId}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.OK)
    fun applyForCreditCard(
        @PathVariable accountHolderId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CreditCardApplicationResponse {
        if (accountHolderId != user.accountHolderId) throw AccountNotFoundException()
        return accountsService.applyForCreditCard(user.userId, user.accountHolderId)
    }
}
