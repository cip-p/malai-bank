package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.UUID

/**
 * E2E-test helpers for hitting the direct-debit-service endpoints exposed under
 * `/api/v1/direct-debits` inside the bundled malai-api container.
 *
 * Direct debits are creditor-originated — there is no customer-facing create surface, so a mandate
 * is lodged bank-side through sandbox-service (see [WithSandboxService.sandboxServiceReceivesLodgeDirectDebit]).
 * These helpers cover the customer-facing list and cancel.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [directDebitServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithDirectDebitService : WithCognitoJwtToken {

    val directDebitServiceHttpClient: HttpClientStub

    fun directDebitServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        directDebitServiceHttpClient.sends(httpRequest.build())

    fun directDebitServiceReceivesGetDirectDebits(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        directDebitServiceHttpClient.sends(
            get()
                .url("/api/v1/direct-debits")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun directDebitServiceReceivesCancelDirectDebit(
        userId: UUID,
        accountHolderId: UUID,
        directDebitId: UUID,
    ): Action<ActionContext> =
        directDebitServiceHttpClient.sends(
            delete()
                .url("/api/v1/direct-debits/$directDebitId")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )
}
