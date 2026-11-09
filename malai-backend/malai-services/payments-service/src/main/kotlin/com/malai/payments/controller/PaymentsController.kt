package com.malai.payments.controller

import com.malai.payments.model.PaymentRequest
import com.malai.payments.model.PaymentResponse
import com.malai.payments.service.PaymentsService
import com.malai.security.MalaiUserWithAccountHolderId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/payments")
class PaymentsController(
    private val service: PaymentsService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.CREATED)
    fun initiate(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @Valid @RequestBody request: PaymentRequest,
    ): PaymentResponse =
        service.initiatePayment(user, request)

    @GetMapping("/{paymentId}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun get(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
        @PathVariable paymentId: UUID,
    ): PaymentResponse =
        service.getPayment(user, paymentId)
}
