package com.malai.client.malai.accounts.onboarding

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.accounts.onboarding.model.CreateOnboardingAccountRequest
import com.malai.client.malai.accounts.onboarding.model.CreateOnboardingAccountResponse
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.POST
import java.util.UUID

@MalaiClientSpringTest
class AccountsOnboardingClientTest : MalaiClientTest(), WithAccountsOnboardingApi {

    @Autowired
    private lateinit var accountsOnboardingClient: AccountsOnboardingClient

    private var lastCreateAccountResponse: CreateOnboardingAccountResponse? = null

    @Test
    fun createAccount() {
        given(createOnboardingAccountReturnsAccountId(someAccountUid()))

        whenever(clientCallsCreateAccount(
            "some-service-token",
            CreateOnboardingAccountRequest(
                userId = someUserId(),
                accountHolderId = someAccountHolderUid(),
                currency = "GBP",
                name = "Personal"
            )
        ))

        then(theLastCreateAccountResponse()) {
            this shouldBe CreateOnboardingAccountResponse(someAccountUid())
        }
        and(madeTheFollowingHttpRequest {
            url == "/account-service/api/v1/internal/onboarding/accounts"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "userId" To someUserId().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "currency" To "GBP"
                "name" To "Personal"
                noExtraProps()
            }
        }
    }

    @Test
    fun activateAccountsForUser() {
        given(activateOnboardingAccountsForUserReturnsSuccess(someUserId()))

        whenever(clientCallsActivateAccountsForUser("some-service-token", someUserId()))

        and(madeTheFollowingHttpRequest {
            url == "/account-service/api/v1/internal/onboarding/accounts/activate/${someUserId()}"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe null
        }
    }

    private fun clientCallsCreateAccount(
        serviceToken: String,
        request: CreateOnboardingAccountRequest,
    ) = Action<ActionContext> {
        lastCreateAccountResponse = accountsOnboardingClient.createAccount(serviceToken, request)
    }

    private fun clientCallsActivateAccountsForUser(serviceToken: String, userId: UUID) = Action<ActionContext> {
        accountsOnboardingClient.activateAccountsForUser(serviceToken, userId)
    }

    private fun theLastCreateAccountResponse() = StateCollector { _ ->
        lastCreateAccountResponse ?: error("No create-account response captured")
    }
}
