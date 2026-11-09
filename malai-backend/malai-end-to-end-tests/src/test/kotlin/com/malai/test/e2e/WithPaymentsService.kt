package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.UUID

/**
 * E2E-test helpers for hitting the payments-service container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [paymentsServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithPaymentsService : WithCognitoJwtToken {

    val paymentsServiceHttpClient: HttpClientStub

    /**
     * Send an arbitrary request to the payments-service container. Tests typically build the
     * request with the `get()/post()/...` DSL and the JWT helpers from
     * [com.malai.test.acceptance.WithCognitoJwtToken] (e.g. `withCustomerJwtTokenForUserIdAndAccountHolderId`).
     */
    fun paymentsServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        paymentsServiceHttpClient.sends(httpRequest.build())

    fun paymentsServiceReceivesInitiatePayment(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        fromAccountId: UUID,
        toSortCode: String,
        toAccountNumber: String,
        amountMinorUnits: Long,
        toName: String? = null,
        currency: String = "GBP",
        description: String? = null,
    ): Action<ActionContext> =
        paymentsServiceHttpClient.sends(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "fromAccountId" to fromAccountId.toString(),
                    "toSortCode" to toSortCode,
                    "toAccountNumber" to toAccountNumber,
                    "toName" to toName,
                    "amountMinorUnits" to amountMinorUnits,
                    "currency" to currency,
                    "description" to description,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun paymentsServiceReceivesGetPayment(
        userId: UUID,
        accountHolderId: UUID,
        paymentId: UUID,
    ): Action<ActionContext> =
        paymentsServiceHttpClient.sends(
            get()
                .url("/api/v1/payments/$paymentId")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun paymentsServiceReceivesInitiateSwiftPayment(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        fromAccountId: UUID,
        toIban: String,
        toBic: String,
        amountMinorUnits: Long,
        currency: String,
        toName: String? = null,
        paymentPurpose: String? = null,
        description: String? = null,
    ): Action<ActionContext> =
        paymentsServiceHttpClient.sends(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "fromAccountId" to fromAccountId.toString(),
                    "toIban" to toIban,
                    "toBic" to toBic,
                    "toName" to toName,
                    "paymentPurpose" to paymentPurpose,
                    "amountMinorUnits" to amountMinorUnits,
                    "currency" to currency,
                    "description" to description,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
