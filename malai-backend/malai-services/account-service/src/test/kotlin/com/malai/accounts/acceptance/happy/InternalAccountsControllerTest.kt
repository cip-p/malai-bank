package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountType.PRIMARY
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.commons.model.Currency.GBP
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class InternalAccountsControllerTest : AccountsAcceptanceTest() {

    @Test
    fun getAccountByIdScopedToHolder() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = PRIMARY,
            currency = GBP,
            name = "Primary Account",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = "65-82-37",
            accountIdentifier = "12345678",
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/internal/accounts/${someAccountUid()}")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "currency" To "GBP"
                "sortCode" To "65-82-37"
                "accountNumber" To "12345678"
                "name" To "Primary Account"
                noExtraProps()
            }
        }
    }

    @Test
    fun resolveAccountBySortCodeAndAccountNumber() = malaiAcceptanceTest {
        given(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            accountType = PRIMARY,
            currency = GBP,
            name = "Primary Account",
        ))
        and(anIdentifierExistsForAccount(
            accountId = someAccountUid(),
            identifierType = SORT_CODE,
            bankIdentifier = "65-82-37",
            accountIdentifier = "87654321",
        ))

        whenever(accountServiceReceives(
            get()
                .url("/api/v1/internal/accounts/by-identifier")
                .queryParam("sortCode", "65-82-37")
                .queryParam("accountNumber", "87654321")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "currency" To "GBP"
                "sortCode" To "65-82-37"
                "accountNumber" To "87654321"
                "name" To "Primary Account"
                noExtraProps()
            }
        }
    }
}
