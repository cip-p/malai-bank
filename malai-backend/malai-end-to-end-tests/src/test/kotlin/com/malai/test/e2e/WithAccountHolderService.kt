package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.*

/**
 * E2E-test helpers for hitting the account-holder-service container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [accountHolderServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithAccountHolderService : WithCognitoJwtToken {

    val accountHolderServiceHttpClient: HttpClientStub

    /**
     * Low-level escape hatch: send an arbitrary request to the account-holder-service container.
     * Prefer adding a named helper (e.g. `accountHolderServiceReceivesCreateAccountHolder(...)`)
     * over constructing an [HttpRequest.HttpRequestBuilder] inline in tests.
     */
    fun accountHolderServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(httpRequest.build())

    fun accountHolderServiceReceivesCreateAccountHolder(
        userId: UUID,
        accountHolderType: String,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            post()
                .url("/api/v1/account-holder")
                .body(jsonBodyOf("accountHolderType" to accountHolderType))
                .contentType("application/json")
                .withCustomerJwtTokenForUserId(userId)
                .build()
        )

    fun accountHolderServiceReceivesCreateIndividual(
        userId: UUID,
        accountHolderId: UUID,
        title: String?,
        firstName: String,
        lastName: String,
        dateOfBirth: String,
        email: String,
        phone: String,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            post()
                .url("/api/v1/account-holder/individual")
                .body(jsonBodyOfNullable(
                    "title" to title,
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "dateOfBirth" to dateOfBirth,
                    "email" to email,
                    "phone" to phone,
                ))
                .contentType("application/json")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountHolderServiceReceivesGetAccountHolder(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            get()
                .url("/api/v1/account-holder")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountHolderServiceReceivesGetIndividual(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            get()
                .url("/api/v1/account-holder/individual")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountHolderServiceReceivesGetAccountHolderName(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            get()
                .url("/api/v1/account-holder/name")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountHolderServiceReceivesGetAllAccountHolders(
        userId: UUID,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            get()
                .url("/api/v1/account-holder/all")
                .withCustomerJwtTokenForUserId(userId)
                .build()
        )

    /**
     * Webapp-style account-holder list fetch: auth via the `access_token` cookie. The cookie's
     * JWT carries the same `sub` claim as `withJwtTokenForUserId` would put on the Authorization
     * header, so the server-side `JwtDecoderFilter` resolves the same user.
     */
    fun accountHolderServiceReceivesGetAllAccountHoldersViaCookie(
        accessTokenCookie: String,
    ): Action<ActionContext> =
        accountHolderServiceHttpClient.sends(
            get()
                .url("/api/v1/account-holder/all")
                .header("Cookie", "access_token=$accessTokenCookie")
                .build()
        )

    private fun jsonBodyOf(vararg pairs: Pair<String, Any>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
