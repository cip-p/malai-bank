package com.malai.sandbox.acceptance

import com.malai.client.malai.accounts.lookup.WithAccountLookup
import com.malai.client.malai.card.WithCards
import com.malai.client.malai.directdebit.WithDirectDebitLodgement
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.sandbox.support.WithSandboxState
import com.malai.sandbox.support.runSandboxService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class SandboxAcceptanceTest :
    MalaiAcceptanceTest(),
    WithMalaiStub,
    WithAccountLookup,
    WithCards,
    WithDirectDebitLodgement,
    WithOAuth2Service,
    WithSandboxState {

    fun sandboxServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun sandboxServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Sandbox Service Acceptance Tests",
                fromSystem = MalaiParty.SandboxService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithMalaiStub.CONTEXT_PATH) -> MalaiParty.MalaiStub
                        url.startsWith(WithAccountLookup.CONTEXT_PATH) -> MalaiParty.AccountService
                        url.startsWith(WithCards.CONTEXT_PATH) -> MalaiParty.CardService
                        url.startsWith(WithDirectDebitLodgement.CONTEXT_PATH) -> MalaiParty.DirectDebitService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runSandboxService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    stubBaseUrl = wiremock.url(WithMalaiStub.CONTEXT_PATH),
                    accountBaseUrl = wiremock.url(WithAccountLookup.CONTEXT_PATH),
                    cardBaseUrl = wiremock.url(WithCards.CONTEXT_PATH),
                    directDebitBaseUrl = wiremock.url(WithDirectDebitLodgement.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
