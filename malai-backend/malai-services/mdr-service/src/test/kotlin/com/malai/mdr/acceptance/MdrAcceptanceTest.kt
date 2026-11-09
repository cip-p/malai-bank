package com.malai.mdr.acceptance

import com.malai.mdr.support.WithMdrEntries
import com.malai.mdr.support.runMdrService
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.audit.AuditTestSupport
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * Thin mdr-service-specific subclass of [MalaiAcceptanceTest]. The shared scaffolding (HTTP client
 * stub, WireMock / JWKS / JWT / time fixtures, DB cleanup, JSON matchers) lives in the base; this
 * class only wires the domain fixtures ([WithMdrEntries]) and the service-specific boot call.
 */
abstract class MdrAcceptanceTest :
    MalaiAcceptanceTest(),
    WithMdrEntries,
    WithTestUuidGenerator,
    AuditTestSupport {

    fun mdrServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "MDR Service Acceptance Tests",
                fromSystem = MalaiParty.MdrService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runMdrService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                )
            }
        }
    }
}
