package com.malai.api

import com.malai.client.malai.ServiceTokenClient
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * E2e-only admin endpoint that drops every [ServiceTokenClient]'s cached Cognito service token.
 * Hit from [MalaiE2ETest.primeJwksStubAndCleanState] before each test so the very next
 * `getToken()` triggers a fresh Cognito round-trip — that round-trip then appears on the test's
 * Kensa sequence diagram alongside the business interactions, instead of being silently fulfilled
 * from a token cached by an earlier test in the same JVM run.
 */
@RestController
@RequestMapping("/internal/admin")
@Profile("e2e-tests")
class EvictServiceTokenCachesController(
    private val serviceTokenClients: List<ServiceTokenClient>,
) {

    @PostMapping("/evict-service-token-caches")
    fun evict(): ResponseEntity<Void> {
        serviceTokenClients.forEach(ServiceTokenClient::evict)
        return ResponseEntity.noContent().build()
    }
}
