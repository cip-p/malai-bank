package com.malai.client.malai.accounts.onboarding

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.*

/**
 * WireMock stubs for the account-service service-to-service onboarding endpoints.
 * Mixed into auth-service acceptance tests so outbound calls are intercepted.
 */
interface WithAccountsOnboardingApi : WithWiremock {

    fun createOnboardingAccountReturnsAccountId(accountId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/accounts"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"accountId":"$accountId"}""")
                )
        )
    }

    fun activateOnboardingAccountsForUserReturnsSuccess(userId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/accounts/activate/$userId"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    companion object {
        const val CONTEXT_PATH = "/account-service"
    }
}
