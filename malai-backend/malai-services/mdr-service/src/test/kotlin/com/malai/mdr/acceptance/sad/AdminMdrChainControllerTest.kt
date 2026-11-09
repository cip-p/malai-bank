package com.malai.mdr.acceptance.sad

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.FORBIDDEN
import java.util.UUID

class AdminMdrChainControllerTest : MdrAcceptanceTest() {

    private val someUserId = UUID.fromString("ad000000-0000-0000-0000-0000000000ff")

    @Test
    fun customerJwtOnAdminMdrChainEndpointReturns403() = malaiAcceptanceTest {
        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withCustomerJwtTokenForUserId(someUserId),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun supportAgentJwtOnAdminMdrChainEndpointReturns403() = malaiAcceptanceTest {
        whenever(mdrServiceReceives(
            get()
                .url("/api/v1/admin/mdr/chain-verification")
                .withJwtTokenForUserId(someUserId, listOf("support_agent")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }
}
