package com.malai.aml.acceptance

import com.malai.aml.scheduler.AmlMonitoringSweep
import com.malai.aml.support.WithAmlCases
import com.malai.aml.support.runAmlService
import com.malai.client.malai.mdr.WithMdrApi
import com.malai.client.malai.payments.WithPaymentsApi
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

abstract class AmlAcceptanceTest :
    MalaiAcceptanceTest(),
    WithTestUuidGenerator,
    WithPaymentsApi,
    WithMdrApi,
    WithOAuth2Service,
    WithAmlCases,
    AuditTestSupport {

    fun amlServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun amlServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun amlServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    fun theMonitoringSweepRuns() = Action<ActionContext> {
        getBean(AmlMonitoringSweep::class.java).sweep()
    }

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "AML Service Acceptance Tests",
                fromSystem = MalaiParty.AmlService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithPaymentsApi.CONTEXT_PATH) -> MalaiParty.PaymentsService
                        url.startsWith(WithMdrApi.CONTEXT_PATH) -> MalaiParty.MdrService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runAmlService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    paymentsBaseUrl = wiremock.url(WithPaymentsApi.CONTEXT_PATH),
                    mdrBaseUrl = wiremock.url(WithMdrApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
