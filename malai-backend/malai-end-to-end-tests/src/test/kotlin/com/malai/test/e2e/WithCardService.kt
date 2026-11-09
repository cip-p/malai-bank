package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.UUID

/**
 * E2E-test helpers for the customer card surface on card-service (`/api/v1/cards/...`), bundled
 * inside the malai-api container. Every endpoint scopes to the authenticated holder, so all of
 * these carry both the customer JWT and the `MAL-Account-Holder-Id` header via
 * [WithCognitoJwtToken.withCustomerJwtTokenForUserIdAndAccountHolderId].
 */
interface WithCardService : WithCognitoJwtToken {

    val cardServiceHttpClient: HttpClientStub

    fun cardServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        cardServiceHttpClient.sends(httpRequest.build())

    fun cardServiceReceivesGetCards(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        cardServiceHttpClient.sends(
            get()
                .url("/api/v1/cards")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun cardServiceReceivesGetCard(
        userId: UUID,
        accountHolderId: UUID,
        cardId: UUID,
    ): Action<ActionContext> =
        cardServiceHttpClient.sends(
            get()
                .url("/api/v1/cards/$cardId")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun cardServiceReceivesFreezeCard(
        userId: UUID,
        accountHolderId: UUID,
        cardId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        cardServiceHttpClient.sends(
            post()
                .url("/api/v1/cards/$cardId/freeze")
                .header("Idempotency-Key", idempotencyKey)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun cardServiceReceivesUnfreezeCard(
        userId: UUID,
        accountHolderId: UUID,
        cardId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        cardServiceHttpClient.sends(
            post()
                .url("/api/v1/cards/$cardId/unfreeze")
                .header("Idempotency-Key", idempotencyKey)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun cardServiceReceivesReportLostOrStolen(
        userId: UUID,
        accountHolderId: UUID,
        cardId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        cardServiceHttpClient.sends(
            post()
                .url("/api/v1/cards/$cardId/report-lost-or-stolen")
                .header("Idempotency-Key", idempotencyKey)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )
}
