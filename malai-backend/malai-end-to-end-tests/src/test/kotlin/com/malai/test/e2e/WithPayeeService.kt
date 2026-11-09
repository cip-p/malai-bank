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
import java.util.*

/**
 * E2E-test helpers for hitting the payee-service container.
 */
interface WithPayeeService : WithCognitoJwtToken {

    val payeeServiceHttpClient: HttpClientStub

    fun payeeServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        payeeServiceHttpClient.sends(httpRequest.build())

    fun payeeServiceReceivesGetPayees(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        payeeServiceHttpClient.sends(
            get()
                .url("/api/v1/payees")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun payeeServiceReceivesCreatePayee(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        name: String,
        sortCode: String,
        accountNumber: String,
        nickname: String? = null,
    ): Action<ActionContext> =
        payeeServiceHttpClient.sends(
            post()
                .url("/api/v1/payees")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "name" to name,
                    "sortCode" to sortCode,
                    "accountNumber" to accountNumber,
                    "nickname" to nickname,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun payeeServiceReceivesDeletePayee(
        userId: UUID,
        accountHolderId: UUID,
        payeeId: UUID,
    ): Action<ActionContext> =
        payeeServiceHttpClient.sends(
            delete()
                .url("/api/v1/payees/$payeeId")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
