package com.malai.exec.acceptance

import com.malai.client.malai.accountholder.metrics.WithAccountHolderMetricsApi
import com.malai.client.malai.card.metrics.WithCardMetricsApi
import com.malai.client.malai.payments.metrics.WithPaymentsMetricsApi
import com.malai.exec.support.runExecService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * exec-service is a composed read over other contexts, so its acceptance tests own no database
 * fixtures at all — every fact arrives from a WireMock lane standing in for the owning service.
 */
abstract class ExecAcceptanceTest :
    MalaiAcceptanceTest(),
    WithAccountHolderMetricsApi,
    WithPaymentsMetricsApi,
    WithCardMetricsApi,
    WithOAuth2Service {

    fun execServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun execServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Exec Service Acceptance Tests",
                fromSystem = MalaiParty.ExecService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithAccountHolderMetricsApi.CONTEXT_PATH) -> MalaiParty.AccountHolderService
                        url.startsWith(WithPaymentsMetricsApi.CONTEXT_PATH) -> MalaiParty.PaymentsService
                        url.startsWith(WithCardMetricsApi.CONTEXT_PATH) -> MalaiParty.CardService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runExecService(
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    accountHolderBaseUrl = wiremock.url(WithAccountHolderMetricsApi.CONTEXT_PATH),
                    paymentsBaseUrl = wiremock.url(WithPaymentsMetricsApi.CONTEXT_PATH),
                    cardBaseUrl = wiremock.url(WithCardMetricsApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
