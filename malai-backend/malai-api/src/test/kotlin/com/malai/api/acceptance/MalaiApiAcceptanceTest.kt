package com.malai.api.acceptance

import com.malai.accountholder.support.WithAccountHolders
import com.malai.accounts.support.WithAccounts
import com.malai.api.support.runMalaiApi
import com.malai.auth.support.WithCognito
import com.malai.auth.support.WithGeneratedDetails
import com.malai.auth.support.WithIdempotencyKeys
import com.malai.auth.support.WithUser
import com.malai.client.malai.accountholder.onboarding.WithAccountHolderOnboardingApi
import com.malai.client.malai.accounts.onboarding.WithAccountsOnboardingApi
import com.malai.client.malai.card.WithCards
import com.malai.client.malai.ledger.WithLedger
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * Unified acceptance-test base class for the bundled [com.malai.api.MalaiApiApplication].
 * Boots the single deployable Spring app once per JVM and mixes in the domain fixture interfaces
 * from auth-service, account-service and account-holder-service so a single test can exercise
 * any of their endpoints.
 *
 * Cross-service onboarding HTTP calls (auth-service → account-holder-service / account-service)
 * are stubbed via WireMock so register-flow tests stay focused on auth-service's outbound
 * behaviour. The receiving services' own DB writes are exercised by their dedicated acceptance
 * tests.
 */
abstract class MalaiApiAcceptanceTest : MalaiAcceptanceTest(),
                                        WithCognito, WithUser, WithGeneratedDetails,
                                        WithAccounts,
                                        WithAccountHolders,
                                        WithAccountHolderOnboardingApi, WithAccountsOnboardingApi,
                                        WithLedger,
                                        WithCards,
                                        WithOAuth2Service,
                                        WithTestUuidGenerator,
                                        WithIdempotencyKeys {

    fun malaiApiReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun malaiApiMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Malai API Acceptance Tests",
                fromSystem = MalaiParty.MalaiApi,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithCognito.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithAccountHolderOnboardingApi.CONTEXT_PATH) -> MalaiParty.AccountHolderService
                        url.startsWith(WithAccountsOnboardingApi.CONTEXT_PATH) -> MalaiParty.AccountService
                        url.startsWith(WithLedger.CONTEXT_PATH) -> MalaiParty.LedgerService
                        url.startsWith(WithCards.CONTEXT_PATH) -> MalaiParty.CardService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runMalaiApi(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoBaseUrl = wiremock.url(WithCognito.CONTEXT_PATH),
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    accountHolderBaseUrl = wiremock.url(WithAccountHolderOnboardingApi.CONTEXT_PATH),
                    accountsBaseUrl = wiremock.url(WithAccountsOnboardingApi.CONTEXT_PATH),
                    ledgerBaseUrl = wiremock.url(WithLedger.CONTEXT_PATH),
                    cardBaseUrl = wiremock.url(WithCards.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
