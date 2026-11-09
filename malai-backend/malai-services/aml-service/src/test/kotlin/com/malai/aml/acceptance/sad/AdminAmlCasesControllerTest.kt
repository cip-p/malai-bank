package com.malai.aml.acceptance.sad

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")
private val SOME_CUSTOMER_USER_ID: UUID = UUID.fromString("cc000000-0000-4000-9000-000000000001")

class AdminAmlCasesControllerTest : AmlAcceptanceTest() {

    @Test
    fun theWorklistWithoutAJwtReturns401() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            get().url("/api/v1/admin/aml/cases"),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
        }
    }

    @Test
    fun aCustomerJwtOnTheWorklistReturns403() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases")
                .withCustomerJwtTokenForUserId(SOME_CUSTOMER_USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe FORBIDDEN
        }
    }

    @Test
    fun anUnknownStatusOnTheWorklistReturns400() = malaiAcceptanceTest {
        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases")
                .queryParam("status", "SUSPICIOUS")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe BAD_REQUEST
        }
    }
}
