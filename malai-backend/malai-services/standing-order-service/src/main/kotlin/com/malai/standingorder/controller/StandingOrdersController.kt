package com.malai.standingorder.controller

import com.malai.security.MalaiUserWithAccountHolderId
import com.malai.standingorder.model.StandingOrderRequest
import com.malai.standingorder.model.StandingOrderResponse
import com.malai.standingorder.model.StandingOrdersResponse
import com.malai.standingorder.service.StandingOrdersService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * `GET` + `POST /api/v1/standing-orders`, `DELETE /api/v1/standing-orders/{id}`.
 *
 * List: `hasAnyRole('CUSTOMER','SUPPORT_AGENT')` mirrors `DirectDebitsController` —
 * the account holder reads their own list and support agents can see it (including
 * cancelled rows) during a customer-support call. ADMIN excluded (no operational
 * use case).
 *
 * Create and cancel: CUSTOMER-only, mirroring `PayeesController` — support agents
 * never mutate customer instructions. Create is registered with the shared
 * Idempotency-Key interceptor (see `StandingOrderServiceConfiguration`).
 */
@RestController
@RequestMapping("/api/v1/standing-orders")
class StandingOrdersController(
    private val service: StandingOrdersService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT')")
    fun list(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): StandingOrdersResponse =
        service.list(user)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @Valid @RequestBody request: StandingOrderRequest,
    ): StandingOrderResponse =
        service.create(user, request)

    @DeleteMapping("/{standingOrderId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @PathVariable standingOrderId: UUID,
    ) {
        service.cancel(user, standingOrderId)
    }
}
