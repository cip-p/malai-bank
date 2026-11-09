package com.malai.client.malai.ledger

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.client.malai.ledger.builders.response.BalancesResponseBuilder
import com.malai.client.malai.ledger.builders.response.EntriesResponseBuilder
import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder
import com.malai.client.malai.ledger.builders.response.RegisterLedgerAccountResponseBuilder
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder
import com.malai.client.malai.ledger.builders.response.TracedTransactionsResponseBuilder
import com.malai.client.malai.ledger.builders.response.TransactionEntriesResponseBuilder
import com.malai.commons.exception.MalaiCode
import com.malai.test.acceptance.WithWiremock
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.UUID

/**
 * WireMock stubs for the internal-only ledger-service endpoints. Mixed into the acceptance tests
 * of any service that calls the ledger (account-service on create, payments-service on post), so
 * outbound ledger calls are intercepted and their request shape can be asserted.
 *
 * Each method takes the response builder; request params are asserted via the captured-request
 * collectors on the service base class.
 */
interface WithLedger : WithWiremock {

    fun registerLedgerAccountReturns(response: RegisterLedgerAccountResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/accounts"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun postTransactionReturns(response: PostTransactionResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/transactions"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun postTransactionReturnsInsufficientFunds() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/transactions"))
                .willReturn(
                    aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "${MalaiCode.LEDGER_INSUFFICIENT_FUNDS.code}"}""")
                )
        )
    }

    fun placeHoldReturns(response: HoldResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/holds"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun placeHoldReturnsInsufficientFunds() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/holds"))
                .willReturn(
                    aResponse()
                        .withStatus(422)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "${MalaiCode.LEDGER_INSUFFICIENT_FUNDS.code}"}""")
                )
        )
    }

    fun settleHoldReturns(response: SettleHoldResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/api/v1/internal/ledger/holds/[^/]+/settle"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun releaseHoldReturns(response: HoldResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/api/v1/internal/ledger/holds/[^/]+/release"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getHoldReturns(holdId: UUID, response: HoldResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/holds/$holdId"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun getHoldReturnsNotFound(holdId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/holds/$holdId"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "${MalaiCode.LEDGER_HOLD_NOT_FOUND.code}"}""")
                )
        )
    }

    fun getBalancesReturns(response: BalancesResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathMatching("$CONTEXT_PATH/api/v1/internal/ledger/accounts/[^/]+/balances"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun listEntriesReturns(response: EntriesResponseBuilder) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathMatching("$CONTEXT_PATH/api/v1/internal/ledger/accounts/[^/]+/entries"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(response.build())
                )
        )
    }

    fun listTransactionEntriesReturns(transactionId: UUID, response: TransactionEntriesResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/transactions/$transactionId/entries"))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(response.build())
                    )
            )
        }

    fun listTransactionEntriesReturnsNotFound(transactionId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/transactions/$transactionId/entries"))
                .willReturn(
                    aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"message": "${MalaiCode.LEDGER_TRANSACTION_NOT_FOUND.code}"}""")
                )
        )
    }

    fun listTransactionsForTraceReturns(traceId: String, response: TracedTransactionsResponseBuilder) =
        Action<GivensContext> {
            wiremock().stubFor(
                get(urlPathEqualTo("$CONTEXT_PATH/api/v1/internal/ledger/transactions"))
                    .withQueryParam("traceId", equalTo(traceId))
                    .willReturn(
                        aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody(response.build())
                    )
            )
        }

    companion object {
        const val CONTEXT_PATH = "/ledger-service"
    }
}
