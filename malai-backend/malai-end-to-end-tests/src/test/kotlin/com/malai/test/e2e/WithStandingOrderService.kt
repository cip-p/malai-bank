package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.UUID

/**
 * E2E-test helpers for hitting the standing-order-service endpoints exposed under
 * `/api/v1/standing-orders` inside the bundled malai-api container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [standingOrderServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithStandingOrderService : WithCognitoJwtToken {

    val standingOrderServiceHttpClient: HttpClientStub

    fun standingOrderServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        standingOrderServiceHttpClient.sends(httpRequest.build())

    fun standingOrderServiceReceivesCreateStandingOrder(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        accountId: UUID,
        payeeId: UUID,
        amountMinorUnits: Long,
        frequency: String,
        startDate: String,
        currency: String = "GBP",
        reference: String? = null,
    ): Action<ActionContext> =
        standingOrderServiceHttpClient.sends(
            post()
                .url("/api/v1/standing-orders")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "accountId" to accountId.toString(),
                    "payeeId" to payeeId.toString(),
                    "amountMinorUnits" to amountMinorUnits,
                    "currency" to currency,
                    "reference" to reference,
                    "frequency" to frequency,
                    "startDate" to startDate,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun standingOrderServiceReceivesGetStandingOrders(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        standingOrderServiceHttpClient.sends(
            get()
                .url("/api/v1/standing-orders")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun standingOrderServiceReceivesCancelStandingOrder(
        userId: UUID,
        accountHolderId: UUID,
        standingOrderId: UUID,
    ): Action<ActionContext> =
        standingOrderServiceHttpClient.sends(
            delete()
                .url("/api/v1/standing-orders/$standingOrderId")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
