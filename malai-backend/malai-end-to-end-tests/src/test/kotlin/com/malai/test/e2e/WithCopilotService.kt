package com.malai.test.e2e

import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import java.util.UUID

interface WithCopilotService : WithCognitoJwtToken {

    val copilotServiceHttpClient: HttpClientStub

    fun copilotServiceExplainsPayment(
        adminUserId: UUID,
        paymentId: UUID,
        idempotencyKey: String,
    ): Action<ActionContext> =
        copilotServiceHttpClient.sends(
            post()
                .url("/api/v1/admin/copilot/explanations")
                .contentType("application/json")
                .header("Idempotency-Key", idempotencyKey)
                .body("""{"subjectType":"PAYMENT","subjectId":"$paymentId"}""")
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun theSourcesCitedByTheExplanation() = StateCollector { _ ->
        val body = copilotServiceHttpClient.lastResponse().body
            ?: throw AssertionError("Response body is null")
        testingObjectMapper.readTree(body).at("/claims").flatMap { claim ->
            claim.at("/sources").values().map { CitedSource(it["type"].asString(), it["id"].asString()) }
        }
    }
}

data class CitedSource(val type: String, val id: String)
