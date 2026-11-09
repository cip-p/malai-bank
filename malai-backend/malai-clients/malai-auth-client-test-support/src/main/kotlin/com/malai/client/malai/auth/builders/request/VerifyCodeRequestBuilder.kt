package com.malai.client.malai.auth.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class VerifyCodeRequestBuilder private constructor() : TestBuilder {

    private var email: String? = null
    private var confirmationCode: String? = null
    private var challengeSession: String? = null

    fun withEmail(email: String?) = apply { this.email = email }
    fun withConfirmationCode(confirmationCode: String?) = apply { this.confirmationCode = confirmationCode }
    fun withChallengeSession(challengeSession: String?) = apply { this.challengeSession = challengeSession }

    override fun build(): String = toJson(
        VerifyCodeRequestPayload(
            email = email,
            confirmationCode = confirmationCode,
            challengeSession = challengeSession
        )
    )

    data class VerifyCodeRequestPayload(
        val email: String?,
        val confirmationCode: String?,
        val challengeSession: String?
    )

    companion object {
        fun aDefaultVerifyCodeRequest() = VerifyCodeRequestBuilder()
            .withEmail("default-email@example.com")
            .withConfirmationCode("default-confirmation-code")
    }
}
