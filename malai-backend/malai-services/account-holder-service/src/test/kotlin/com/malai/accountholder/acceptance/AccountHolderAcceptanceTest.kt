package com.malai.accountholder.acceptance

import com.malai.accountholder.support.WithAccountHolders
import com.malai.accountholder.support.runAccountHolderService
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
 * Thin account-holder-service-specific subclass of [com.malai.test.acceptance.MalaiAcceptanceTest]. All of the common
 * scaffolding (HTTP client stub, WireMock / JWKS / JWT / time fixtures, DB cleanup, JSON
 * matchers, etc.) lives in the shared module; this class only wires up:
 *
 * - the domain fixture interface ([com.malai.accountholder.support.WithAccountHolders])
 * - the service-specific boot call ([com.malai.accountholder.support.runAccountHolderService]) and WireMock downstream-party
 *   lookup
 */
abstract class AccountHolderAcceptanceTest :
    MalaiAcceptanceTest(),
    WithAccountHolders,
    WithTestUuidGenerator,
    AuditTestSupport {

    fun accountHolderServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Account Holder Service Acceptance Tests",
                fromSystem = MalaiParty.AccountHolderService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runAccountHolderService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                )
            }
        }
    }
}
