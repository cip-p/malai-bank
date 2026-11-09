package com.malai.accountholder.controller

import com.malai.accountholder.model.CreateOnboardingAccountHolderRequest
import com.malai.accountholder.model.CreateOnboardingAccountHolderResponse
import com.malai.accountholder.model.CreateOnboardingAddressRequest
import com.malai.accountholder.model.CreateOnboardingIndividualRequest
import com.malai.accountholder.service.OnboardingService
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
@RequestMapping("/api/v1/internal/onboarding/account-holders")
class OnboardingController(private val service: OnboardingService) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(CREATED)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun createAccountHolder(
        @Valid @RequestBody request: CreateOnboardingAccountHolderRequest,
    ): CreateOnboardingAccountHolderResponse {
        log.debug("internal: create onboarding account-holder for userId={}", request.userId)
        return service.createAccountHolder(request)
    }

    @PostMapping("/{accountHolderId}/individual", consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(NO_CONTENT)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun createIndividual(
        @PathVariable accountHolderId: UUID,
        @Valid @RequestBody request: CreateOnboardingIndividualRequest,
    ) {
        log.debug("internal: create onboarding individual for accountHolderId={}", accountHolderId)
        service.createIndividual(accountHolderId, request)
    }

    @PostMapping("/{accountHolderId}/address", consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(NO_CONTENT)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun createAddress(
        @PathVariable accountHolderId: UUID,
        @Valid @RequestBody request: CreateOnboardingAddressRequest,
    ) {
        log.debug("internal: create onboarding address for accountHolderId={}", accountHolderId)
        service.createAddress(accountHolderId, request)
    }

    @PostMapping("/activate/{userId}")
    @ResponseStatus(NO_CONTENT)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun activateByUserId(@PathVariable userId: UUID) {
        log.debug("internal: activate account-holder(s) for user {}", userId)
        service.activateByUserId(userId)
    }
}
