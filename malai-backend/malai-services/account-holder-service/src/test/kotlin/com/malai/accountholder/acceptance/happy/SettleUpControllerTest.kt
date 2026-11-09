package com.malai.accountholder.acceptance.happy

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.SettleUpProfileStatus
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class SettleUpControllerTest : AccountHolderAcceptanceTest() {

    @Test
    fun getSettleUpProfile() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.INDIVIDUAL))
        and(aSettleUpProfileExists(
            someSettleUpProfileUid(),
            someAccountHolderUid(),
            settleUpLink = "https://settleup.malaibank.com/amanda.hanks",
            status = SettleUpProfileStatus.ACTIVE
        ))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/settle-up/profile")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "settleUpLink" To "https://settleup.malaibank.com/amanda.hanks"
                "status" To "ACTIVE"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSettleUpProfileWithInactiveStatus() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.INDIVIDUAL))
        and(aSettleUpProfileExists(
            someSettleUpProfileUid(),
            someAccountHolderUid(),
            settleUpLink = "https://settleup.malaibank.com/john.doe",
            status = SettleUpProfileStatus.INACTIVE
        ))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/settle-up/profile")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "settleUpLink" To "https://settleup.malaibank.com/john.doe"
                "status" To "INACTIVE"
                noExtraProps()
            }
        }
    }

    @Test
    fun getSettleUpProfileWithClosedStatus() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), AccountHolderType.INDIVIDUAL))
        and(aSettleUpProfileExists(
            someSettleUpProfileUid(),
            someAccountHolderUid(),
            settleUpLink = "https://settleup.malaibank.com/closed.user",
            status = SettleUpProfileStatus.CLOSED
        ))

        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/settle-up/profile")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "settleUpLink" To "https://settleup.malaibank.com/closed.user"
                "status" To "CLOSED"
                noExtraProps()
            }
        }
    }
}
