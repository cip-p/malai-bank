package com.malai.accountholder.acceptance.sad

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderType
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND

class SettleUpControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun getSettleUpProfileReturns404WhenProfileNotFound() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.INDIVIDUAL))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/settle-up/profile")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8986"
                noExtraProps()
            }
        }
    }
}
