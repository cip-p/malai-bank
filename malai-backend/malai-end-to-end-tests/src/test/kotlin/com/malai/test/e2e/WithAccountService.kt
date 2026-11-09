package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.*

/**
 * E2E-test helpers for hitting the account-service container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [accountServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithAccountService : WithCognitoJwtToken {

    val accountServiceHttpClient: HttpClientStub

    /**
     * Send an arbitrary request to the account-service container. Tests typically build the
     * request with the `get()/post()/...` DSL and the JWT helpers from
     * [com.malai.test.acceptance.WithCognitoJwtToken] (e.g. `withJwtTokenForUserIdAndAccountHolderId`).
     */
    fun accountServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        accountServiceHttpClient.sends(httpRequest.build())

    fun accountServiceReceivesCreateAccount(
        userId: UUID,
        accountHolderId: UUID,
        accountType: String,
        currency: String,
        name: String,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            post()
                .url("/api/v1/accounts")
                .body(jsonBodyOf("accountType" to accountType, "currency" to currency, "name" to name))
                .contentType("application/json")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesTransfer(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
        fromAccountId: UUID,
        toSortCode: String,
        toAccountNumber: String,
        amountMinorUnits: Long,
        currency: String = "GBP",
        description: String? = null,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            post()
                .url("/api/v1/accounts/$fromAccountId/transfers")
                .header("Idempotency-Key", idempotencyKey)
                .contentType("application/json")
                .body(jsonBodyOfNullable(
                    "toSortCode" to toSortCode,
                    "toAccountNumber" to toAccountNumber,
                    "amountMinorUnits" to amountMinorUnits,
                    "currency" to currency,
                    "description" to description,
                ))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetBalance(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/balance")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetAccounts(
        userId: UUID,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    /**
     * Webapp-style account list fetch: auth via the `access_token` cookie, account-holder still
     * carried in the `MAL-Account-Holder-Id` header (it's a routing header, not a credential, so
     * it stays on the header path even in cookie mode).
     */
    fun accountServiceReceivesGetAccountsViaCookie(
        accessTokenCookie: String,
        accountHolderId: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts")
                .header("Cookie", "access_token=$accessTokenCookie")
                .header("MAL-Account-Holder-Id", accountHolderId.toString())
                .build()
        )

    fun accountServiceReceivesGetSpending(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
        month: String,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/spending")
                .queryParam("month", month)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetTransactions(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/transactions")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetForecast(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/forecast")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesOverrideCategory(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
        entryId: UUID,
        category: String,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            put()
                .url("/api/v1/accounts/$accountUid/transactions/$entryId/category")
                .contentType("application/json")
                .body(jsonBodyOf("category" to category))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesClearCategoryOverride(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
        entryId: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            delete()
                .url("/api/v1/accounts/$accountUid/transactions/$entryId/category")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesCreditCardApplication(
        userId: UUID,
        accountHolderId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            post()
                .url("/api/v1/credit-card-applications/$accountHolderId")
                .header("Idempotency-Key", idempotencyKey)
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetCreditSummary(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/credit-summary")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    fun accountServiceReceivesGetStatement(
        userId: UUID,
        accountHolderId: UUID,
        accountUid: UUID,
        yyyymm: String,
    ): Action<ActionContext> =
        accountServiceHttpClient.sends(
            get()
                .url("/api/v1/accounts/$accountUid/statements/$yyyymm")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(userId, accountHolderId)
                .build()
        )

    private fun jsonBodyOf(vararg pairs: Pair<String, Any>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))

    private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
