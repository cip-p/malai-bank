package com.malai.standingorder.acceptance

import com.malai.client.malai.payments.WithPaymentsApi
import com.malai.standingorder.support.WithStandingOrders
import com.malai.standingorder.support.runStandingOrderService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class StandingOrdersAcceptanceTest : MalaiAcceptanceTest(),
                                              WithStandingOrders, WithTestUuidGenerator,
                                              WithPaymentsApi, WithOAuth2Service {

    fun standingOrderServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun standingOrderServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun standingOrderServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Standing Order Service Acceptance Tests",
                fromSystem = MalaiParty.StandingOrderService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithPaymentsApi.CONTEXT_PATH) -> MalaiParty.PaymentsService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runStandingOrderService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    paymentsBaseUrl = wiremock.url(WithPaymentsApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
