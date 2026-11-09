package com.malai.payments.controller

import com.malai.payments.model.DepositsTotalResponse
import com.malai.payments.model.PaymentsStoppedTotalResponse
import com.malai.payments.service.InternalPaymentsMetricsService
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

@RestController
@RequestMapping("/api/v1/internal/payments")
class InternalPaymentsMetricsController(
    private val service: InternalPaymentsMetricsService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping("/deposits-total", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun depositsTotal(
        @RequestParam @DateTimeFormat(iso = DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DATE_TIME) until: Instant,
    ): DepositsTotalResponse {
        log.debug("internal: deposits settled between {} and {}", from, until)
        return service.depositsSettledBetween(from, until)
    }

    @GetMapping("/stopped-total", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun paymentsStoppedTotal(
        @RequestParam @DateTimeFormat(iso = DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DATE_TIME) until: Instant,
    ): PaymentsStoppedTotalResponse {
        log.debug("internal: payments stopped between {} and {}", from, until)
        return service.paymentsStoppedBetween(from, until)
    }
}
