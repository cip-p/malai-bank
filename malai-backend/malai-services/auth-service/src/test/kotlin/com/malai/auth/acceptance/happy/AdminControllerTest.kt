package com.malai.auth.acceptance.happy

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

/**
 * Quick 260512-ttc — happy paths for `GET /api/v1/admin/audit-chain/verification`.
 *
 * Closes Phase 2 ROADMAP success criterion #5: a `support_agent` JWT receives
 * 403 on a write path (covered in `sad/TransfersControllerTest`), and a non-ADMIN
 * (customer) JWT receives 403 on an ADMIN endpoint (covered in
 * [com.malai.auth.acceptance.sad.AdminControllerTest]). Here we prove the
 * positive: an `admin` JWT walks the chain successfully on a clean genesis chain
 * and gets back a flat OK verdict.
 */
class AdminControllerTest : AuthAcceptanceTest() {

    @Test
    fun adminCanTriggerAuditChainVerificationOnAGenesisChain() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        whenever(authServiceReceives(
            get()
                .url("/api/v1/admin/audit-chain/verification")
                .withJwtTokenForUserId(someUserId(), listOf("admin"))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "OK"
                "rowsVerified" To 0
                noExtraProps()
            }
        }
    }
}
