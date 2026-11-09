package com.malai.accounts.acceptance

import com.malai.accounts.support.WithAccounts
import com.malai.accounts.support.WithTestAccountNumberGenerator
import com.malai.accounts.support.runAccountsService
import com.malai.client.malai.card.WithCards
import com.malai.client.malai.directdebit.WithDirectDebitsApi
import com.malai.client.malai.insights.WithInsightsApi
import com.malai.client.malai.ledger.WithLedger
import com.malai.client.malai.payments.WithPaymentsApi
import com.malai.client.malai.standingorder.WithStandingOrdersApi
import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.test.acceptance.*
import com.malai.test.acceptance.audit.AuditTestSupport
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * Thin account-service-specific subclass of [com.malai.test.acceptance.MalaiAcceptanceTest]. All of the common
 * scaffolding (HTTP client stub, WireMock / JWKS / JWT / time fixtures, DB cleanup, JSON matchers,
 * etc.) lives in the shared module; this class only wires up:
 *
 * - the domain fixture interface ([com.malai.accounts.support.WithAccounts])
 * - the downstream-service stub interfaces ([WithMalaiStub] for CoP federation)
 * - the service-specific boot call ([com.malai.accounts.support.runAccountsService]) and WireMock
 *   downstream-party lookup
 */
abstract class AccountsAcceptanceTest :
    MalaiAcceptanceTest(),
    WithAccounts,
    WithTestUuidGenerator,
    WithTestAccountNumberGenerator,
    WithMalaiStub,
    WithLedger,
    WithCards,
    WithInsightsApi,
    WithStandingOrdersApi,
    WithDirectDebitsApi,
    WithOAuth2Service,
    AuditTestSupport {

    fun accountServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun accountServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun accountServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Account Service Acceptance Tests",
                fromSystem = MalaiParty.AccountService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithMalaiStub.CONTEXT_PATH) -> MalaiParty.MalaiStub
                        url.startsWith(WithPaymentsApi.CONTEXT_PATH) -> MalaiParty.PaymentsService
                        url.startsWith(WithLedger.CONTEXT_PATH) -> MalaiParty.LedgerService
                        url.startsWith(WithCards.CONTEXT_PATH) -> MalaiParty.CardService
                        url.startsWith(WithInsightsApi.CONTEXT_PATH) -> MalaiParty.InsightsService
                        url.startsWith(WithStandingOrdersApi.CONTEXT_PATH) -> MalaiParty.StandingOrderService
                        url.startsWith(WithDirectDebitsApi.CONTEXT_PATH) -> MalaiParty.DirectDebitService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runAccountsService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    stubBaseUrl = wiremock.url(WithMalaiStub.CONTEXT_PATH),
                    paymentsBaseUrl = wiremock.url(WithPaymentsApi.CONTEXT_PATH),
                    ledgerBaseUrl = wiremock.url(WithLedger.CONTEXT_PATH),
                    cardBaseUrl = wiremock.url(WithCards.CONTEXT_PATH),
                    insightsBaseUrl = wiremock.url(WithInsightsApi.CONTEXT_PATH),
                    standingOrderBaseUrl = wiremock.url(WithStandingOrdersApi.CONTEXT_PATH),
                    directDebitBaseUrl = wiremock.url(WithDirectDebitsApi.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
