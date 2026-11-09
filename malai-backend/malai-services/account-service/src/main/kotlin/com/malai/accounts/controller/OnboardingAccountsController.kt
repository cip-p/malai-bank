package com.malai.accounts.controller

import com.malai.accounts.model.CreateOnboardingAccountRequest
import com.malai.accounts.model.CreateOnboardingAccountResponse
import com.malai.accounts.service.OnboardingAccountsService
import com.malai.security.InternalEndpoint
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.*

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

@RestController
@RequestMapping("/api/v1/internal/onboarding/accounts")
class OnboardingAccountsController(private val service: OnboardingAccountsService) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(CREATED)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun createAccount(
        @Valid @RequestBody request: CreateOnboardingAccountRequest,
    ): CreateOnboardingAccountResponse {
        log.debug("internal: create onboarding account for accountHolderId={}", request.accountHolderId)
        return service.createAccount(request)
    }

    @PostMapping("/activate/{userId}")
    @ResponseStatus(NO_CONTENT)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun activateForUser(@PathVariable userId: UUID) {
        log.debug("internal: activate accounts for user {}", userId)
        service.activateAccountsForUser(userId)
    }
}
