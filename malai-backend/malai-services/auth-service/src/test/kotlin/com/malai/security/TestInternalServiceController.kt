package com.malai.security

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only controller mounted under `/api/v1/internal/...` so [ServiceJwtFilter] can be exercised
 * end-to-end via MockMvc — request hits the real filter chain, and the controller decodes the
 * [ServicePrincipal] populated by the filter so the test can assert on it.
 */
@RestController
@RequestMapping("/api/v1/internal/test-security")
class TestInternalServiceController {

    @GetMapping(value = ["decode-service-principal"], produces = [APPLICATION_JSON_VALUE])
    fun decodeServicePrincipal(@AuthenticationPrincipal principal: ServicePrincipal): ServicePrincipalView {
        return ServicePrincipalView(principal.clientId, principal.scopes.sorted())
    }
}

data class ServicePrincipalView(
    val clientId: String,
    val scopes: List<String>,
)
