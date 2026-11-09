package com.malai.client.malai.auth.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder
import java.util.*

class RefreshTokenRequestBuilder private constructor() : TestBuilder {

    private var userId: UUID? = null
    private var refreshToken: String? = null

    fun withUserId(userId: UUID?) = apply { this.userId = userId }
    fun withRefreshToken(refreshToken: String?) = apply { this.refreshToken = refreshToken }

    override fun build(): String = toJson(RefreshTokenRequest(userId, refreshToken))

    data class RefreshTokenRequest(
        val userId: UUID?,
        val refreshToken: String?,
    )

    companion object {
        fun aDefaultRefreshTokenRequest() = RefreshTokenRequestBuilder()
            .withUserId(UUID.fromString("00000000-0000-0000-0000-000000000001"))
            .withRefreshToken("some-refresh-token")
    }
}
