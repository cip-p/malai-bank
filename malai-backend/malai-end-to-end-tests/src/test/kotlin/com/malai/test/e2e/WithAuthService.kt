package com.malai.test.e2e

import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpClientStub
import com.malai.test.acceptance.kensa.stub.HttpRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.ActionContext
import java.util.*

/**
 * E2E-test helpers for hitting the auth-service container.
 *
 * Implementations (typically [MalaiE2ETest] subclasses) expose the per-service
 * [authServiceHttpClient] so these helpers don't reach into [MalaiE2ETestContext] directly.
 */
interface WithAuthService : WithCognitoJwtToken {

    val authServiceHttpClient: HttpClientStub

    fun authServiceReceives(httpRequest: HttpRequest.HttpRequestBuilder): Action<ActionContext> =
        authServiceHttpClient.sends(httpRequest.build())

    /**
     * `GET /api/v1/admin/audit-chain/verification` — the on-demand proof that the 'global' audit
     * chain is intact, the counterpart to [WithMdrService.mdrServiceVerifiesChain] over the 'mdr'
     * scope. Lives on auth-service because that is where the ADMIN operational surface sits.
     */
    fun authServiceVerifiesAuditChain(adminUserId: UUID): Action<ActionContext> =
        authServiceHttpClient.sends(
            get()
                .url("/api/v1/admin/audit-chain/verification")
                .withJwtTokenForUserId(adminUserId, listOf("admin"))
                .build(),
        )

    fun authServiceReceivesRegisterPasswordless(
        firstName: String,
        lastName: String,
        email: String,
    ): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/register")
                .body(testingObjectMapper.writeValueAsString(mapOf(
                    "firstName" to firstName,
                    "lastName" to lastName,
                    "email" to email,
                )))
                .contentType("application/json")
                .build()
        )

    fun authServiceReceivesVerifyCode(email: String, confirmationCode: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/verify-code")
                .body(jsonBodyOf("email" to email, "confirmationCode" to confirmationCode))
                .contentType("application/json")
                .build()
        )

    /**
     * Non-browser Verification of a login Challenge: the Challenge session rides the request body
     * field rather than the `challenge_session` cookie. Mirrors how a CLI/mobile client echoes the
     * session value it read off the `login` response's `Set-Cookie` header.
     */
    fun authServiceReceivesVerifyCodeWithChallengeSession(
        email: String,
        confirmationCode: String,
        challengeSession: String,
    ): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/verify-code")
                .body(jsonBodyOf(
                    "email" to email,
                    "confirmationCode" to confirmationCode,
                    "challengeSession" to challengeSession,
                ))
                .contentType("application/json")
                .build()
        )

    /**
     * Browser Verification of a login Challenge: the Challenge session rides the `challenge_session`
     * HttpOnly cookie set by the previous `login` response, mirroring the webapp cookie path.
     */
    fun authServiceReceivesVerifyCodeViaChallengeCookie(
        email: String,
        confirmationCode: String,
        challengeSessionCookie: String,
    ): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/verify-code")
                .body(jsonBodyOf("email" to email, "confirmationCode" to confirmationCode))
                .header("Cookie", "challenge_session=$challengeSessionCookie")
                .contentType("application/json")
                .build()
        )

    fun authServiceReceivesLoginPasswordless(email: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/login")
                .body(jsonBodyOf("email" to email))
                .contentType("application/json")
                .build()
        )

    fun authServiceReceivesRefreshToken(userId: UUID, refreshToken: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/refresh-token")
                .body(jsonBodyOf("userId" to userId, "refreshToken" to refreshToken))
                .contentType("application/json")
                .build()
        )

    /**
     * Webapp cold-load refresh: the body is empty (`{}`). The refresh token rides in the
     * `refresh_token` HttpOnly cookie and the user id is derived from the `access_token`
     * cookie's JWT — signature verified, expiry ignored (ADR-0002) — so refresh works from
     * cookies alone with no identity in the body. Mirrors the webapp's 401→refresh fetch.
     */
    fun authServiceReceivesRefreshTokenViaCookies(
        accessTokenCookie: String,
        refreshTokenCookie: String,
    ): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/refresh-token")
                .body(jsonBodyOf())
                .header("Cookie", "access_token=$accessTokenCookie; refresh_token=$refreshTokenCookie")
                .contentType("application/json")
                .build()
        )

    fun authServiceReceivesSignOut(userId: UUID): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/signout")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
                .build()
        )

    /**
     * Webapp-style signout: no `Authorization` header — the access token rides in the
     * `access_token` HttpOnly cookie. The response should clear both cookies.
     */
    fun authServiceReceivesSignOutViaCookie(accessTokenCookie: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            post()
                .url("/api/v1/auth/signout")
                .header("Cookie", "access_token=$accessTokenCookie")
                .build()
        )

    fun authServiceReceivesGetUser(userId: UUID): Action<ActionContext> =
        authServiceHttpClient.sends(
            get()
                .url("/api/v1/user")
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
                .build()
        )

    /** Webapp-style /api/v1/user fetch — auth via the `access_token` cookie. */
    fun authServiceReceivesGetUserViaCookie(accessTokenCookie: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            get()
                .url("/api/v1/user")
                .header("Cookie", "access_token=$accessTokenCookie")
                .build()
        )

    fun authServiceReceivesUpdateUser(userId: UUID, firstName: String, lastName: String): Action<ActionContext> =
        authServiceHttpClient.sends(
            put()
                .url("/api/v1/user")
                .body(jsonBodyOf("firstName" to firstName, "lastName" to lastName))
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
                .contentType("application/json")
                .build()
        )

    private fun jsonBodyOf(vararg pairs: Pair<String, Any>): String =
        testingObjectMapper.writeValueAsString(mapOf(*pairs))
}
