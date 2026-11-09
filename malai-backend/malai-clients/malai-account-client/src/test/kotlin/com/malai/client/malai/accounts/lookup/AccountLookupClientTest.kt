package com.malai.client.malai.accounts.lookup

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.accounts.lookup.model.InternalAccountResponse
import com.malai.test.acceptance.MalaiClientTest
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET

@MalaiClientSpringTest
class AccountLookupClientTest : MalaiClientTest(), WithAccountLookup {

    @Autowired
    private lateinit var accountLookupClient: AccountLookupClient

    private var lastResponse: InternalAccountResponse? = null

    @Test
    fun getAccount() {
        given(getAccountReturns(
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            sortCode = "65-82-37",
            accountNumber = "12345678",
            name = "Main Account",
        ))

        whenever(clientCallsGetAccount("some-service-token"))

        then(theLastResponse()) {
            this shouldBe InternalAccountResponse(
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                currency = "GBP",
                sortCode = "65-82-37",
                accountNumber = "12345678",
                name = "Main Account",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/account-service/api/v1/internal/accounts/${someAccountUid()}?accountHolderId=${someAccountHolderUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun getAccountReturnsNullWhenNotFound() {
        given(getAccountReturnsNotFound(someAccountUid(), anotherAccountHolderUid()))

        whenever(clientCallsGetAccountForHolder("some-service-token", anotherAccountHolderUid()))

        then(theLastResponseOrNull()) {
            this shouldBe null
        }
    }

    @Test
    fun resolveBySortCodeAndAccountNumber() {
        given(resolveAccountReturns(
            sortCode = "65-82-37",
            accountNumber = "87654321",
            accountId = someAccountUid(),
            accountHolderId = someAccountHolderUid(),
            currency = "GBP",
            name = "Main Account",
        ))

        whenever(clientCallsResolve("some-service-token", "65-82-37", "87654321"))

        then(theLastResponse()) {
            this shouldBe InternalAccountResponse(
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                currency = "GBP",
                sortCode = "65-82-37",
                accountNumber = "87654321",
                name = "Main Account",
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/account-service/api/v1/internal/accounts/by-identifier?sortCode=65-82-37&accountNumber=87654321"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Accept"] shouldBe "application/json"
            headers["X-Malai-Source"] shouldBe "malai-client-tests"
        }
    }

    @Test
    fun resolveReturnsNullWhenNotFound() {
        given(resolveAccountReturnsNotFound("65-82-37", "00000000"))

        whenever(clientCallsResolve("some-service-token", "65-82-37", "00000000"))

        then(theLastResponseOrNull()) {
            this shouldBe null
        }
    }

    private fun clientCallsGetAccount(serviceToken: String) = Action<ActionContext> {
        lastResponse = accountLookupClient.getAccount(serviceToken, someAccountUid(), someAccountHolderUid())
    }

    private fun clientCallsGetAccountForHolder(serviceToken: String, accountHolderId: java.util.UUID) =
        Action<ActionContext> {
            lastResponse = accountLookupClient.getAccount(serviceToken, someAccountUid(), accountHolderId)
        }

    private fun clientCallsResolve(serviceToken: String, sortCode: String, accountNumber: String) =
        Action<ActionContext> {
            lastResponse = accountLookupClient.resolveBySortCodeAndAccountNumber(serviceToken, sortCode, accountNumber)
        }

    private fun theLastResponse() = StateCollector { _ ->
        lastResponse ?: error("No account-lookup response captured")
    }

    private fun theLastResponseOrNull() = StateCollector { _ -> lastResponse }
}
