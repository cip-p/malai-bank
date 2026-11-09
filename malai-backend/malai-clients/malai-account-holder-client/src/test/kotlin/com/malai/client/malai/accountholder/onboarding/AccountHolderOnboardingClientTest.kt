package com.malai.client.malai.accountholder.onboarding

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAccountHolderRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAccountHolderResponse
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAddressRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingIndividualRequest
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
class AccountHolderOnboardingClientTest : MalaiClientTest(), WithAccountHolderOnboardingApi {

    @Autowired
    private lateinit var accountHolderOnboardingClient: AccountHolderOnboardingClient

    private var lastCreateAccountHolderResponse: CreateOnboardingAccountHolderResponse? = null

    @Test
    fun createAccountHolder() {
        given(createOnboardingAccountHolderReturnsAccountHolderId(someAccountHolderUid()))

        whenever(clientCallsCreateAccountHolder(
            "some-service-token",
            CreateOnboardingAccountHolderRequest(userId = someUserId())
        ))

        then(theLastCreateAccountHolderResponse()) {
            this shouldBe CreateOnboardingAccountHolderResponse(someAccountHolderUid())
        }
        and(madeTheFollowingHttpRequest {
            url == "/account-holder-service/api/v1/internal/onboarding/account-holders"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "userId" To someUserId().toString()
                noExtraProps()
            }
        }
    }

    @Test
    fun createIndividual() {
        given(createOnboardingIndividualReturnsSuccess(someAccountHolderUid()))

        whenever(clientCallsCreateIndividual(
            "some-service-token",
            someAccountHolderUid(),
            CreateOnboardingIndividualRequest(
                firstName = "Alice",
                lastName = "Smith",
                dateOfBirth = "1990-04-12",
                email = "alice@example.com"
            )
        ))

        and(madeTheFollowingHttpRequest {
            url == "/account-holder-service/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/individual"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "firstName" To "Alice"
                "lastName" To "Smith"
                "dateOfBirth" To "1990-04-12"
                "email" To "alice@example.com"
                noExtraProps()
            }
        }
    }

    @Test
    fun createAddress() {
        given(createOnboardingAddressReturnsSuccess(someAccountHolderUid()))

        whenever(clientCallsCreateAddress(
            "some-service-token",
            someAccountHolderUid(),
            CreateOnboardingAddressRequest(
                line1 = "10 Downing Street",
                line2 = null,
                city = "London",
                postcode = "SW1A 2AA",
                country = "GB"
            )
        ))

        and(madeTheFollowingHttpRequest {
            url == "/account-holder-service/api/v1/internal/onboarding/account-holders/${someAccountHolderUid()}/address"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body containsProp json {
                "line1" To "10 Downing Street"
                "line2" To null
                "city" To "London"
                "postcode" To "SW1A 2AA"
                "country" To "GB"
                noExtraProps()
            }
        }
    }

    @Test
    fun activateByUserId() {
        given(activateOnboardingAccountHolderForUserReturnsSuccess(someUserId()))

        whenever(clientCallsActivateByUserId("some-service-token", someUserId()))

        and(madeTheFollowingHttpRequest {
            url == "/account-holder-service/api/v1/internal/onboarding/account-holders/activate/${someUserId()}"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
            body shouldBe null
        }
    }

    private fun clientCallsCreateAccountHolder(
        serviceToken: String,
        request: CreateOnboardingAccountHolderRequest,
    ) = Action<ActionContext> {
        lastCreateAccountHolderResponse = accountHolderOnboardingClient.createAccountHolder(serviceToken, request)
    }

    private fun clientCallsCreateIndividual(
        serviceToken: String,
        accountHolderId: UUID,
        request: CreateOnboardingIndividualRequest,
    ) = Action<ActionContext> {
        accountHolderOnboardingClient.createIndividual(serviceToken, accountHolderId, request)
    }

    private fun clientCallsCreateAddress(
        serviceToken: String,
        accountHolderId: UUID,
        request: CreateOnboardingAddressRequest,
    ) = Action<ActionContext> {
        accountHolderOnboardingClient.createAddress(serviceToken, accountHolderId, request)
    }

    private fun clientCallsActivateByUserId(serviceToken: String, userId: UUID) = Action<ActionContext> {
        accountHolderOnboardingClient.activateByUserId(serviceToken, userId)
    }

    private fun theLastCreateAccountHolderResponse() = StateCollector { _ ->
        lastCreateAccountHolderResponse ?: error("No create-account-holder response captured")
    }
}
