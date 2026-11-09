package com.malai.api.acceptance.happy

import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType
import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.client.malai.accounts.builders.request.CreateAccountRequestBuilder.Companion.aDefaultCreateAccountRequest
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.commons.model.Currency.GBP
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant

class AccountsApiTest : MalaiApiAcceptanceTest() {

    @Test
    fun createAccountIsSavedAsPrimary() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(someAccountUid())))
        and(issueCardReturns(anIssueCardResponse().withCardId(someCardUid()).withAccountId(someAccountUid())))
        and(theNextGeneratedUuidWillBe(someAccountUid(), someDefaultCategory()))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(
            malaiApiReceives(
                post()
                    .url("/api/v1/accounts")
                    .body(
                        aDefaultCreateAccountRequest()
                            .withAccountType("PRIMARY")
                            .withCurrency(someCurrency())
                            .withName("Personal")
                    )
                    .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
            )
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To someAccountUid().toString()
                "accountType" To "PRIMARY"
                "defaultCategory" To someDefaultCategory().toString()
                "currency" To someCurrency()
                "createdAt" To "2026-10-15T10:30:00Z"
                "name" To "Personal"
                noExtraProps()
            }
        }

        and(theAccountInTheDatabase(someAccountUid())) {
            this shouldBeEqualUsingFields AccountEntity(
                accountId = someAccountUid(),
                accountType = AccountType.PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = GBP,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                name = "Personal",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = ACTIVE,
            )
        }
    }
}
