package com.malai.directdebit.acceptance

import com.malai.client.malai.stub.cop.WithMalaiStub
import com.malai.directdebit.support.WithDirectDebits
import com.malai.directdebit.support.runDirectDebitService
import com.malai.test.acceptance.CapturedHttpRequest
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class DirectDebitsAcceptanceTest : MalaiAcceptanceTest(),
                                            WithDirectDebits, WithTestUuidGenerator, WithMalaiStub {

    fun directDebitServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    fun directDebitServiceMadeTheFollowingHttpRequest(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequest(matching)

    fun directDebitServiceMadeTheFollowingHttpRequests(matching: CapturedHttpRequest.() -> Boolean) =
        malaiServiceMadeTheFollowingHttpRequests(matching)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Direct Debit Service Acceptance Tests",
                fromSystem = MalaiParty.DirectDebitService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        url.startsWith(WithMalaiStub.CONTEXT_PATH) -> MalaiParty.MalaiStub
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runDirectDebitService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                    stubBaseUrl = wiremock.url(WithMalaiStub.CONTEXT_PATH),
                )
            }
        }
    }
}
