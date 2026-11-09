package com.malai.auth.acceptance.sad

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.FORBIDDEN

/**
 * Quick 260512-ttc — sad paths for `GET /api/v1/admin/audit-chain/verification`.
 *
 * Pins Phase 2 ROADMAP success criterion #5: a `customer` JWT must NOT be able
 * to reach an ADMIN-only endpoint. The matching happy-path coverage lives in
 * [com.malai.auth.acceptance.happy.AdminControllerTest].
 *
 * BACK-06: the `@PreAuthorize("hasRole('ADMIN')")` on
 * [com.malai.auth.controller.AdminController.verifyAuditChain] is enforced at
 * runtime via `@EnableMethodSecurity` (Plan 02-16). Without the per-method
 * `@PreAuthorize` and the matching `AuthorizationDeniedException` handler in
 * `HttpExceptionHandler` (D-2.35), this assertion would return 500 not 403.
 */
class AdminControllerTest : AuthAcceptanceTest() {

    @Test
    fun customerJwtOnAdminAuditChainEndpointReturns403() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            get()
                .url("/api/v1/admin/audit-chain/verification")
                .withCustomerJwtTokenForUserId(someUserId())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun supportAgentJwtOnAdminAuditChainEndpointReturns403() = malaiAcceptanceTest {
        whenever(authServiceReceives(
            get()
                .url("/api/v1/admin/audit-chain/verification")
                .withJwtTokenForUserId(someUserId(), listOf("support_agent"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }
}
