package com.malai.accounts.acceptance.sad

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountType.PRIMARY
import com.malai.commons.model.Currency.GBP
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.UNAUTHORIZED

class InternalAccountsControllerTest : AccountsAcceptanceTest() {

    @Test
    fun getAccountForAnotherHolderReturns404() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = PRIMARY,
            currency = GBP,
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/internal/accounts/${someAccountUid()}")
                .queryParam("accountHolderId", anotherAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
            }
        }
    }

    @Test
    fun resolveUnknownIdentifierReturns404() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/internal/accounts/by-identifier")
                .queryParam("sortCode", "65-82-37")
                .queryParam("accountNumber", "00000000")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8998"
            }
        }
    }

    @Test
    fun getAccountWithoutServiceJwtReturns401() = malaiAcceptanceTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/internal/accounts/${someAccountUid()}")
                .queryParam("accountHolderId", someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
