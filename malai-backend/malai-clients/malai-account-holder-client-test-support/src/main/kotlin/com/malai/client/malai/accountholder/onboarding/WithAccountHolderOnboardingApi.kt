package com.malai.client.malai.accountholder.onboarding

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

/**
 * WireMock stubs for the account-holder-service service-to-service onboarding endpoints.
 *
 * Mixed into auth-service acceptance tests so the orchestrator's HTTP calls are intercepted
 * by WireMock — the auth-service test asserts the *outbound* requests; the actual DB writes
 * are the account-holder-service's responsibility and are tested there.
 */
interface WithAccountHolderOnboardingApi : WithWiremock {

    fun createOnboardingAccountHolderReturnsAccountHolderId(accountHolderId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/account-holders"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"accountHolderId":"$accountHolderId"}""")
                )
        )
    }

    fun createOnboardingIndividualReturnsSuccess(accountHolderId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/account-holders/$accountHolderId/individual"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    fun createOnboardingAddressReturnsSuccess(accountHolderId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/account-holders/$accountHolderId/address"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    fun activateOnboardingAccountHolderForUserReturnsSuccess(userId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/onboarding/account-holders/activate/$userId"))
                .willReturn(aResponse().withStatus(204))
        )
    }

    companion object {
        const val CONTEXT_PATH = "/account-holder-service"
    }
}
