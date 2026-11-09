package com.malai.accounts.acceptance.happy

import com.malai.accounts.acceptance.AccountsAcceptanceTest
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountType.PRIMARY
import com.malai.accounts.entity.IdentifierType.IBAN_BIC
import com.malai.accounts.entity.IdentifierType.SORT_CODE
import com.malai.client.malai.accounts.builders.request.CreateOnboardingAccountRequestBuilder.Companion.aDefaultCreateOnboardingAccountRequest
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder.Companion.aRegisterLedgerAccountResponse
import com.malai.commons.model.Currency.GBP
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import java.time.Instant

class OnboardingAccountsControllerTest : AccountsAcceptanceTest() {

    @Test
    fun createOnboardingAccount() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(registerLedgerAccountReturns(aRegisterLedgerAccountResponse().withAccountId(someAccountUid())))
        and(issueCardReturns(anIssueCardResponse().withCardId(someCardUid()).withAccountId(someAccountUid())))
        and(theNextGeneratedUuidWillBe(someAccountUid(), someDefaultCategory()))
        and(theNextGeneratedAccountNumberWillBe("87654321"))
        and(nowIs("2026-10-15T10:30:00Z"))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts")
                .body(aDefaultCreateOnboardingAccountRequest()
                    .withUserId(someUserId())
                    .withAccountHolderId(someAccountHolderUid())
                    .withCurrency("GBP")
                    .withName("Personal"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "accountId" To someAccountUid().toString()
                noExtraProps()
            }
        }

        and(theAccountInTheDatabase(someAccountUid())) {
            this shouldBe AccountEntity(
                accountId = someAccountUid(),
                accountType = PRIMARY,
                defaultCategory = someDefaultCategory(),
                currency = GBP,
                createdAt = Instant.parse("2026-10-15T10:30:00Z"),
                name = "Personal",
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                status = PENDING,
            )
        }

        and(theIdentifiersInTheDatabase(someAccountUid())) {
            this shouldHaveSize 2

            this[0].identifierType shouldBe SORT_CODE
            this[0].accountId shouldBe someAccountUid()
            this[0].bankIdentifier shouldBe "65-82-37"
            this[0].accountIdentifier shouldBe "87654321"

            this[1].identifierType shouldBe IBAN_BIC
            this[1].accountId shouldBe someAccountUid()
            this[1].bankIdentifier shouldBe "MALAGB22XXX"
            this[1].accountIdentifier shouldBe "GB76MALA65823787654321"
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNT_ONBOARDED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "Account"
            this[0]["resource_id"] shouldBe someAccountUid().toString()
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/accounts"
        }) {
            method shouldBe POST
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "currency" To "GBP"
                "kind" To "CUSTOMER"
                "guardPolicy" To "NO_OVERDRAFT"
                noExtraProps()
            }
        }

        and(accountServiceMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards"
        }) {
            method shouldBe POST
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "cardholderName" To "Personal"
                "variant" To "DEBIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun activateAccountsByUserId() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountExists(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            userId = someUserId(),
            status = PENDING,
        ))

        whenever(accountServiceReceives(
            post()
                .url("/api/v1/internal/onboarding/accounts/activate/${someUserId()}")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theAccountInTheDatabase(someAccountUid())) {
            status shouldBe ACTIVE
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "ACCOUNTS_ACTIVATED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe someUserId()
            this[0]["resource_type"] shouldBe "User"
            this[0]["resource_id"] shouldBe someUserId().toString()
        }
    }
}
