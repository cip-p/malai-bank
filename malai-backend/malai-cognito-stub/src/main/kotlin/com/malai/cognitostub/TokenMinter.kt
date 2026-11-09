package com.malai.cognitostub

import com.nimbusds.jwt.JWTClaimsSet
import java.time.Clock
import java.time.Duration
import java.util.Date
import java.util.UUID

class TokenMinter(
    private val signingKey: SigningKey,
    private val issuerProvider: () -> String,
    private val clock: Clock = Clock.systemUTC(),
) {

    fun accessToken(user: StubUser, clientId: String): String = signingKey.sign(
        baseClaims(user.sub.toString(), "access", ACCESS_TOKEN_TTL)
            .claim("client_id", clientId)
            .claim("username", user.email)
            .claim("scope", "aws.cognito.signin.user.admin")
            .withGroups(user.groups)
            .build()
    )

    fun idToken(user: StubUser, clientId: String): String = signingKey.sign(
        baseClaims(user.sub.toString(), "id", ACCESS_TOKEN_TTL)
            .audience(clientId)
            .claim("email", user.email)
            .claim("email_verified", true)
            .claim("name", user.name)
            .claim("cognito:username", user.email)
            .withGroups(user.groups)
            .build()
    )

    fun refreshToken(user: StubUser): String = signingKey.sign(
        baseClaims(user.sub.toString(), "refresh", REFRESH_TOKEN_TTL).build()
    )

    fun serviceAccessToken(clientId: String, scope: String): String = signingKey.sign(
        baseClaims(clientId, "access", ACCESS_TOKEN_TTL)
            .claim("client_id", clientId)
            .claim("scope", scope)
            .claim("version", 2)
            .build()
    )

    fun subjectOfRefreshToken(token: String): UUID? = subjectOf(token, "refresh")

    fun subjectOfAccessToken(token: String): UUID? = subjectOf(token, "access")

    private fun subjectOf(token: String, tokenUse: String): UUID? =
        signingKey.verifiedClaims(token)
            ?.takeIf { runCatching { it.getStringClaim("token_use") }.getOrNull() == tokenUse }
            ?.takeIf { it.expirationTime.after(Date.from(clock.instant())) }
            ?.subject
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun baseClaims(subject: String, tokenUse: String, ttl: Duration): JWTClaimsSet.Builder {
        val now = clock.instant()
        return JWTClaimsSet.Builder()
            .subject(subject)
            .issuer(issuerProvider())
            .jwtID(UUID.randomUUID().toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plus(ttl)))
            .claim("token_use", tokenUse)
    }

    private fun JWTClaimsSet.Builder.withGroups(groups: Set<String>): JWTClaimsSet.Builder =
        if (groups.isEmpty()) this else claim("cognito:groups", groups.toList())

    companion object {
        val ACCESS_TOKEN_TTL: Duration = Duration.ofHours(1)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
    }
}
