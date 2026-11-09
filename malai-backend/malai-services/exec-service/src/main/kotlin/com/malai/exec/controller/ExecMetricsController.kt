package com.malai.exec.controller

import com.malai.exec.model.ExecMetricsResponse
import com.malai.exec.model.MetricsWindow
import com.malai.exec.service.ExecMetricsService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

// The Exec surface's one read. Not holder-scoped — these are the bank's numbers, not a customer's,
// so there is no @AuthenticationPrincipal here and no ownership filter to apply; the role is the
// whole of the authorization decision.
//
// Per ADR-0010, MANAGER — the narrow executive role — offers the Exec surface and nothing else, so a
// bank can give its CFO the dashboard without also handing over the power to rule on decisions that
// move money. ADMIN remains the superset and keeps Exec, hence hasAnyRole('MANAGER','ADMIN').
@RestController
@RequestMapping("/api/v1/exec")
class ExecMetricsController(
    private val service: ExecMetricsService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping("/metrics", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    fun metrics(
        @RequestParam(required = false) window: MetricsWindow?,
    ): ExecMetricsResponse {
        val resolved = window ?: MetricsWindow.TODAY
        log.debug("exec: metrics for window {}", resolved)
        return service.metrics(resolved)
    }
}
