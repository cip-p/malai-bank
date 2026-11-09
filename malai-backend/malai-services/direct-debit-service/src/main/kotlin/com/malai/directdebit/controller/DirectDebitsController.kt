package com.malai.directdebit.controller

import com.malai.directdebit.model.DirectDebitsResponse
import com.malai.directdebit.service.DirectDebitsService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * `GET /api/v1/direct-debits` + `DELETE /api/v1/direct-debits/{id}`.
 *
 * List: `hasAnyRole('CUSTOMER','SUPPORT_AGENT')` — the account holder reads their
 * own list and support agents can see it (including cancelled rows) during a
 * customer-support call. ADMIN excluded (no operational use case).
 *
 * Cancel: CUSTOMER-only, mirroring `StandingOrdersController.cancel` — support
 * agents never mutate customer instructions.
 */
@RestController
@RequestMapping("/api/v1/direct-debits")
class DirectDebitsController(
    private val service: DirectDebitsService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT')")
    fun list(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): DirectDebitsResponse =
        service.list(user)

    @DeleteMapping("/{directDebitId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @PathVariable directDebitId: UUID,
    ) {
        service.cancel(user, directDebitId)
    }
}
