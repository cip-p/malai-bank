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
 * E2E-test helpers for hitting the sandbox-service endpoints exposed under `/api/v1/sandbox/...`
 * inside the bundled malai-api container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [sandboxServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithSandboxService : WithCognitoJwtToken {

    val sandboxServiceHttpClient: HttpClientStub

    fun sandboxServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        sandboxServiceHttpClient.sends(httpRequest.build())

    fun sandboxServiceReceivesGetCounterparties(userId: UUID): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            get()
                .url("/api/v1/sandbox/counterparties")
                .withCustomerJwtTokenForUserId(userId)
                .build()
        )

    fun sandboxServiceReceivesInboundPayment(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        rail: String,
        creditedAccountId: UUID,
        originatorSortCode: String,
        originatorAccountNumber: String,
        originatorName: String,
        amountMinorUnits: Long,
        reference: String? = null,
    ): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            post()
                .url("/api/v1/sandbox/inbound-payments")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "rail" to rail,
                    "creditedAccountId" to creditedAccountId.toString(),
                    "originatorSortCode" to originatorSortCode,
                    "originatorAccountNumber" to originatorAccountNumber,
                    "originatorName" to originatorName,
                    "amountMinorUnits" to amountMinorUnits,
                    "reference" to reference,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun sandboxServiceReceivesLodgeDirectDebit(
        userId: UUID,
        accountHolderId: UUID,
        accountId: UUID,
        creditorSortCode: String,
        creditorAccountNumber: String,
        amountMinorUnits: Long,
        frequency: String,
        startDate: String,
        reference: String? = null,
    ): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            post()
                .url("/api/v1/sandbox/direct-debits")
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "accountId" to accountId.toString(),
                    "creditorSortCode" to creditorSortCode,
                    "creditorAccountNumber" to creditorAccountNumber,
                    "amountMinorUnits" to amountMinorUnits,
                    "reference" to reference,
                    "frequency" to frequency,
                    "startDate" to startDate,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun sandboxServiceReceivesGetCardMerchants(userId: UUID): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            get()
                .url("/api/v1/sandbox/card-merchants")
                .withCustomerJwtTokenForUserId(userId)
                .build()
        )

    fun sandboxServiceReceivesCardPurchase(
        userId: UUID,
        accountHolderId: UUID,
        cardId: UUID,
        merchantId: String,
        amountMinorUnits: Long,
    ): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            post()
                .url("/api/v1/sandbox/card-purchases")
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "cardId" to cardId.toString(),
                    "merchantId" to merchantId,
                    "amountMinorUnits" to amountMinorUnits,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun sandboxServiceReceivesCardPresentNow(userId: UUID): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            post()
                .url("/api/v1/sandbox/card-presentments")
                .withCustomerJwtTokenForUserId(userId)
                .build()
        )

    fun sandboxServiceReceivesCardRefund(
        userId: UUID,
        accountHolderId: UUID,
        authorisationId: UUID,
    ): Action<ActionContext> =
        sandboxServiceHttpClient.sends(
            post()
                .url("/api/v1/sandbox/card-refunds")
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "authorisationId" to authorisationId.toString(),
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
