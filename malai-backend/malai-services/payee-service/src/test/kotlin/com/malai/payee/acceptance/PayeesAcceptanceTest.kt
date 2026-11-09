package com.malai.payee.acceptance

import com.malai.payee.support.WithPayees
import com.malai.payee.support.WithStandingOrdersRawSql
import com.malai.payee.support.runPayeeService
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.interaction.PartyLookupByUrl
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

abstract class PayeesAcceptanceTest : MalaiAcceptanceTest(),
                                      WithPayees, WithStandingOrdersRawSql, WithTestUuidGenerator {

    fun payeeServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Payee Service Acceptance Tests",
                fromSystem = MalaiParty.PayeeService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        else -> MalaiParty.Unknown
                    }
                },
            ) { wiremock ->
                runPayeeService(
                    databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL,
                    cognitoJwtIssuer = wiremock.url(WithIdentityProvider.CONTEXT_PATH),
                )
            }
        }
    }
}
