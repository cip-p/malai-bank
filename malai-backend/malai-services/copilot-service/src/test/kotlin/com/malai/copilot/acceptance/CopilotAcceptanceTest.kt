package com.malai.copilot.acceptance

import com.malai.client.malai.ledger.WithLedger
import com.malai.client.malai.mdr.WithMdrApi
import com.malai.client.malai.payments.WithPaymentsApi
import com.malai.copilot.support.runCopilotService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.audit.AuditTestSupport
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class CopilotAcceptanceTest :
    MalaiAcceptanceTest(),
    WithTestUuidGenerator,
    WithPaymentsApi,
    WithLedger,
    WithMdrApi,
    WithOAuth2Service,
    AuditTestSupport {

    fun copilotServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun copilotServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun copilotServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Copilot Service Acceptance Tests",
                fromSystem = MalaiParty.CopilotService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithPaymentsApi.CONTEXT_PATH) -> MalaiParty.PaymentsService
                        url.startsWith(WithLedger.CONTEXT_PATH) -> MalaiParty.LedgerService
                        url.startsWith(WithMdrApi.CONTEXT_PATH) -> MalaiParty.MdrService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runCopilotService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    paymentsBaseUrl = wiremock.url(WithPaymentsApi.CONTEXT_PATH),
                    ledgerBaseUrl = wiremock.url(WithLedger.CONTEXT_PATH),
                    mdrBaseUrl = wiremock.url(WithMdrApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
