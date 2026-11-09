package com.malai.accounts.controller

import com.malai.accounts.model.ConfirmationOfPayeeRequest
import com.malai.accounts.model.ConfirmationOfPayeeResponse
import com.malai.accounts.service.ConfirmationOfPayeeService
import com.malai.security.MalaiUser
import com.malai.security.NonMutatingEndpoint
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/confirmation-of-payee")
class ConfirmationOfPayeeController(
    private val service: ConfirmationOfPayeeService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @NonMutatingEndpoint
    fun confirm(
        @AuthenticationPrincipal user: MalaiUser,
        @Valid @RequestBody body: ConfirmationOfPayeeRequest,
    ): ConfirmationOfPayeeResponse =
        service.confirm(body)
}
