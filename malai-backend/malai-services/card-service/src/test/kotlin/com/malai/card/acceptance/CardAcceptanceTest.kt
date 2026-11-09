package com.malai.card.acceptance

import com.malai.card.support.WithCardService
import com.malai.card.support.WithTestPanGenerator
import com.malai.card.support.runCardService
import com.malai.client.malai.ledger.WithLedger
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithOAuth2Service
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class CardAcceptanceTest :
    MalaiAcceptanceTest(),
    WithCardService,
    WithTestUuidGenerator,
    WithTestPanGenerator,
    WithLedger,
    WithOAuth2Service {

    fun cardServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun cardServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun cardServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    fun noLedgerHoldWasPlaced() = CallableAction {
        val testId = TestIdContext.value.value
        val holdCalls = wiremock().allServeEvents.filter {
            it.request.url.endsWith("/api/v1/internal/ledger/holds") &&
                it.request.headers.getHeader(TestId.HEADER).containsValue(testId)
        }
        check(holdCalls.isEmpty()) {
            "expected no ledger hold to be placed but card-service called: ${holdCalls.map { it.request.url }}"
        }
    }

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Card Service Acceptance Tests",
                fromSystem = MalaiParty.CardService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithOAuth2Service.CONTEXT_PATH) -> MalaiParty.OAuth2Service
                        url.startsWith(WithLedger.CONTEXT_PATH) -> MalaiParty.LedgerService
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runCardService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    ledgerBaseUrl = wiremock.url(WithLedger.CONTEXT_PATH),
                    cognitoServiceTokenEndpointUrl = wiremock.url("${WithOAuth2Service.CONTEXT_PATH}/oauth2/token"),
                )
            }
        }
    }
}
