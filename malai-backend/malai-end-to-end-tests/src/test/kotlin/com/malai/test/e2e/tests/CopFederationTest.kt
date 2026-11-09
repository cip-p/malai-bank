package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.CoP
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK

class CopFederationTest : MalaiE2ETest() {

    @Test
    fun confirmationOfPayeeFederatesToStubForUnknownDestination() = malaiTest {
        given(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/confirmation-of-payee")
                .header("Idempotency-Key", "e2e-cop-1")
                .contentType("application/json")
                .body("""{"name":"Alice Walker","sortCode":"60-00-01","accountNumber":"12345678"}""")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), accountHolderId)
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "result" To "MATCH"
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AccountService, CoP) {
            method == POST && url.endsWith("/cop/lookup")
        }) {
            body containsProp json {
                "sortCode" To "60-00-01"
                "accountNumber" To "12345678"
                "name" To "Alice Walker"
                noExtraProps()
            }
        }
    }
}
