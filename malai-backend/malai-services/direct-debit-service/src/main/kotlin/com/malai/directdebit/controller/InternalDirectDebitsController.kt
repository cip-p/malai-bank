package com.malai.directdebit.controller

import com.malai.directdebit.model.DirectDebitResponse
import com.malai.directdebit.model.InternalDirectDebitsResponse
import com.malai.directdebit.model.LodgeMandateRequest
import com.malai.directdebit.service.DirectDebitLodgementService
import com.malai.directdebit.service.DirectDebitsService
import com.malai.security.InternalEndpoint
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

/**
 * `POST /api/v1/internal/direct-debits` — service-to-service mandate lodgement, reached by
 * sandbox-service through `DirectDebitLodgementClient`. Direct debits are creditor-originated;
 * there is no customer-facing create surface, so this write lives behind the service-token scope
 * (`ServiceJwtFilter` + the internal filter chain), not a customer role.
 *
 * `GET /api/v1/internal/direct-debits?accountId=…` — per-account read of an account's ACTIVE
 * mandates, consumed by account-service when composing the balance forecast. A read, but it
 * authenticates with the same internal service scope every internal endpoint uses: the shared
 * internal filter chain gates every internal path on `onboarding.write`, so there is no separate
 * read scope to reuse (introducing one is a shared malai-security change, deferred — same
 * conscious deviation the standing-order internal read endpoint documents).
 */
@RestController
@RequestMapping("/api/v1/internal/direct-debits")
class InternalDirectDebitsController(
    private val lodgementService: DirectDebitLodgementService,
    private val service: DirectDebitsService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(CREATED)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun lodge(@Valid @RequestBody request: LodgeMandateRequest): DirectDebitResponse =
        lodgementService.lodge(request)

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun activeForAccount(
        @RequestParam accountId: UUID,
    ): InternalDirectDebitsResponse = service.activeForAccount(accountId)
}
