package com.malai.standingorder.controller

import com.malai.security.InternalEndpoint
import com.malai.standingorder.model.InternalStandingOrdersResponse
import com.malai.standingorder.service.StandingOrdersService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

/**
 * Internal, service-token per-account read of an account's ACTIVE standing orders, consumed
 * by account-service when composing the balance forecast. A read, but it authenticates with
 * the same internal service scope every internal endpoint uses: the shared internal filter
 * chain gates every internal path on `onboarding.write`, so there is no separate read scope
 * to reuse (introducing one is a shared malai-security change, deferred).
 */
@RestController
@RequestMapping("/api/v1/internal/standing-orders")
class InternalStandingOrdersController(
    private val service: StandingOrdersService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun activeForAccount(
        @RequestParam accountId: UUID,
    ): InternalStandingOrdersResponse = service.activeForAccount(accountId)
}
