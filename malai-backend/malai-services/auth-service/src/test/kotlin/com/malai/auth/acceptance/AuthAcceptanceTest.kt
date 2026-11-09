package com.malai.auth.acceptance

import com.malai.auth.acceptance.audit.AuditTestSupport
import com.malai.auth.support.WithCognito
import com.malai.auth.support.WithGeneratedDetails
import com.malai.auth.support.WithIdempotencyKeys
import com.malai.auth.support.WithUser
import com.malai.auth.support.runAuthService
import com.malai.client.malai.accountholder.onboarding.WithAccountHolderOnboardingApi
import com.malai.client.malai.accounts.onboarding.WithAccountsOnboardingApi
import com.malai.test.acceptance.*
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountHolderService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.IdentityProvider
import com.malai.test.acceptance.kensa.MalaiParty.Companion.OAuth2Service
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * Thin auth-service-specific subclass of [com.malai.test.acceptance.MalaiAcceptanceTest]. All of the common scaffolding
 * (HTTP client stub, WireMock / JWKS / JWT / time fixtures, DB cleanup, JSON matchers, etc.)
 * lives in the shared module; this class only wires up:
 *
 * - the domain fixture interfaces ([com.malai.auth.support.WithCognito], [com.malai.auth.support.WithUser])
 * - the downstream-service stubs ([WithAccountHolderOnboardingApi], [WithAccountsOnboardingApi],
 *   [WithOAuth2Service]) — register orchestrator calls these via WireMock
 * - the service-specific boot call ([com.malai.auth.support.runAuthService]) and WireMock downstream-party lookup
 */
abstract class AuthAcceptanceTest : MalaiAcceptanceTest(),
    WithCognito, WithUser, WithGeneratedDetails, WithIdempotencyKeys,
    WithAccountHolderOnboardingApi, WithAccountsOnboardingApi,
    WithOAuth2Service, AuditTestSupport {

    fun authServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun authServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun authServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Auth Service Acceptance Tests",
                fromSystem = MalaiParty.AuthService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithCognito.CONTEXT_PATH) -> IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> OAuth2Service
                        url.startsWith(WithAccountHolderOnboardingApi.CONTEXT_PATH) -> AccountHolderService
                        url.startsWith(WithAccountsOnboardingApi.CONTEXT_PATH) -> AccountService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runAuthService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoBaseUrl = wiremock.url(WithCognito.CONTEXT_PATH),
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    accountHolderBaseUrl = wiremock.url(WithAccountHolderOnboardingApi.CONTEXT_PATH),
                    accountsBaseUrl = wiremock.url(WithAccountsOnboardingApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
