package com.malai.accountholder.controller

import com.malai.accountholder.model.AccountHoldersCreatedResponse
import com.malai.accountholder.service.InternalAccountHolderMetricsService
import com.malai.security.InternalEndpoint
import org.slf4j.LoggerFactory
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

// Service-to-service metric read. The caller supplies the window as two instants and this context
// answers for its own rows only; deciding what window a figure covers is the composing surface's
// job, not this one's, so nothing here knows about banking days or timezones.
//
// Internal routes stay out of the public contract by the convention openapi.yaml states. The scope
// is the shared malai-internal/onboarding.write rather than a read-only one because
// SecurityConfig.internalServicesFilterChain admits exactly that scope on every internal path; a
// narrower scope would be a Cognito resource-server change, not a controller change.
@RestController
@RequestMapping("/api/v1/internal/account-holders")
class InternalAccountHolderMetricsController(
    private val service: InternalAccountHolderMetricsService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping("/created-count", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun accountHoldersCreated(
        @RequestParam @DateTimeFormat(iso = DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DATE_TIME) until: Instant,
    ): AccountHoldersCreatedResponse {
        log.debug("internal: account holders created between {} and {}", from, until)
        return service.accountHoldersCreated(from, until)
    }
}
