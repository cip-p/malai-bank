package com.malai.payments.controller

import com.malai.payments.model.InternalPaymentRequest
import com.malai.payments.model.InternalPaymentResponse
import com.malai.payments.model.PaymentResponse
import com.malai.payments.model.SettledPaymentsResponse
import com.malai.payments.service.InternalPaymentsReadService
import com.malai.payments.service.PaymentsService
import com.malai.security.InternalEndpoint
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

@RestController
@RequestMapping("/api/v1/internal/payments")
class InternalPaymentsController(
    private val service: PaymentsService,
    private val readService: InternalPaymentsReadService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    @ResponseStatus(HttpStatus.CREATED)
    fun initiate(
        @Valid @RequestBody request: InternalPaymentRequest,
    ): PaymentResponse =
        service.initiateScheduledPayment(request)

    @GetMapping("/{paymentId}", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun getPayment(
        @PathVariable paymentId: UUID,
    ): InternalPaymentResponse =
        readService.getPayment(paymentId)

    @GetMapping("/settled", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun settledPayments(
        @RequestParam @DateTimeFormat(iso = DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DATE_TIME) until: Instant,
    ): SettledPaymentsResponse =
        readService.settledPaymentsIn(from, until)
}
