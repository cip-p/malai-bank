package com.malai.payments.acceptance

import com.malai.client.malai.accounts.lookup.WithAccountLookup
import com.malai.client.malai.ledger.WithLedger
import com.malai.client.malai.mdr.WithMdrApi
import com.malai.payments.screening.ScreeningMode
import com.malai.payments.screening.ScreeningProperties
import com.malai.payments.support.WithPayments
import com.malai.payments.support.runPaymentsService
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
import org.junit.jupiter.api.BeforeEach

abstract class PaymentsAcceptanceTest :
    MalaiAcceptanceTest(),
    WithPayments,
    WithTestUuidGenerator,
    WithLedger,
    WithMdrApi,
    WithOAuth2Service,
    AuditTestSupport {

    @BeforeEach
    fun resetScreeningModeToTheDeployedDefault() {
        getBean(ScreeningProperties::class.java).mode = ScreeningMode.SHADOW
    }

    fun paymentsServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun paymentsServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun paymentsServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Payments Service Acceptance Tests",
                fromSystem = MalaiParty.PaymentsService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithLedger.CONTEXT_PATH) -> MalaiParty.LedgerService
                        url.startsWith(WithAccountLookup.CONTEXT_PATH) -> MalaiParty.AccountService
                        url.startsWith(WithMdrApi.CONTEXT_PATH) -> MalaiParty.MdrService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runPaymentsService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    ledgerBaseUrl = wiremock.url(WithLedger.CONTEXT_PATH),
                    accountBaseUrl = wiremock.url(WithAccountLookup.CONTEXT_PATH),
                    mdrBaseUrl = wiremock.url(WithMdrApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
