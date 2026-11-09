package com.malai.payee.controller

import com.malai.payee.model.PayeeRequest
import com.malai.payee.model.PayeeResponse
import com.malai.payee.model.PayeesResponse
import com.malai.payee.service.PayeesService
import com.malai.security.MalaiUserWithAccountHolderId
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
 * Plan 02-14 — saved-payee CRUD for the demo's "send money" flow.
 *
 * `@PreAuthorize("hasRole('CUSTOMER')")` is decoration-only at runtime today
 * (D-2.21 — Plan 02-16 enables `@EnableMethodSecurity`); the annotation
 * satisfies Plan 02-08's `everyControllerMethodHasPreAuthorize` ArchUnit rule
 * and documents the intended access policy.
 */
@RestController
@RequestMapping("/api/v1/payees")
class PayeesController(
    private val service: PayeesService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun list(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): PayeesResponse =
        service.list(user)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @Valid @RequestBody request: PayeeRequest,
    ): PayeeResponse =
        service.addPayee(user, request)

    @DeleteMapping("/{payeeId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remove(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @PathVariable payeeId: UUID,
    ) {
        service.removePayee(user, payeeId)
    }
}
