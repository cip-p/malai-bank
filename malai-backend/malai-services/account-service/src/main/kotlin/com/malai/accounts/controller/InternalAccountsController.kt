package com.malai.accounts.controller

import com.malai.accounts.model.InternalAccountResponse
import com.malai.accounts.service.InternalAccountsService
import com.malai.security.InternalEndpoint
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

@RestController
@RequestMapping("/api/v1/internal/accounts")
class InternalAccountsController(private val service: InternalAccountsService) {

    @GetMapping("/{accountId}", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun getAccount(
        @PathVariable accountId: UUID,
        @RequestParam accountHolderId: UUID,
    ): InternalAccountResponse = service.getAccount(accountId, accountHolderId)

    @GetMapping("/by-identifier", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun resolveByIdentifier(
        @RequestParam sortCode: String,
        @RequestParam accountNumber: String,
    ): InternalAccountResponse = service.resolveByIdentifier(sortCode, accountNumber)
}
