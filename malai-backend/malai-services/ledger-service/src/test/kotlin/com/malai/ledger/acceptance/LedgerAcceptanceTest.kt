package com.malai.ledger.acceptance

import com.malai.ledger.support.WithLedgerService
import com.malai.ledger.support.runLedgerService
import com.malai.test.acceptance.MalaiAcceptanceTest
import com.malai.test.acceptance.MalaiAcceptanceTestContext
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithTestUuidGenerator
import com.malai.test.acceptance.kensa.MalaiParty
import com.malai.test.acceptance.kensa.stub.HttpRequest
import dev.kensa.Action
import dev.kensa.ActionContext

/**
 * Thin ledger-service-specific subclass of [MalaiAcceptanceTest]. The ledger has no downstream
 * dependencies and no security of its own (when bundled in malai-api the aggregator's
 * ServiceJwtFilter protects its internal ledger endpoints); standalone it just persists to the
 * `ledger` Postgres schema.
 */
abstract class LedgerAcceptanceTest :
    MalaiAcceptanceTest(),
    WithLedgerService,
    WithTestUuidGenerator {

    fun ledgerServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        serviceReceives(httpRequest)

    companion object {
        init {
            MalaiAcceptanceTestContext.boot(
                reportTitle = "Ledger Service Acceptance Tests",
                fromSystem = MalaiParty.LedgerService,
                toSystemLookup = { url ->
                    when {
                        url.startsWith(WithIdentityProvider.CONTEXT_PATH) -> MalaiParty.IdentityProvider
                        else -> MalaiParty.Unknown
                    }
                },
            ) {
                runLedgerService(databaseUrl = MalaiAcceptanceTestContext.DATABASE_URL)
            }
        }
    }
}
