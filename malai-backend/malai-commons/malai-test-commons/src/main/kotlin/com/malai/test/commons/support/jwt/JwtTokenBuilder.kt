package com.malai.test.commons.support.jwt

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class JwtTokenBuilder {

    private var userId: UUID? = null
    private var accountHolderId: UUID? = null
    private var type: String? = null
    private var ttlMinutes: Long? = null
    private var clientId: String? = null
    private var scopes: List<String>? = null
    private var cognitoGroups: List<String>? = null

    companion object {
        fun userJwtTokenBuilderWithDefaultValues(): JwtTokenBuilder {
            return JwtTokenBuilder()
                .withUserId(UUID.randomUUID())
                .withType("access")
                .withTtlMinutes(60)
        }

        /**
         * Defaults for a Cognito service (client_credentials) access token. `sub` is set to
         * the client id (this matches Cognito's behaviour for client-credentials tokens) and
         * a single scope is granted.
         */
        fun serviceJwtTokenBuilderWithDefaultValues(): JwtTokenBuilder {
            return JwtTokenBuilder()
                .withType("access")
                .withTtlMinutes(60)
                .withClientId("test-service-client-id")
                .withScopes(listOf("malai-internal/onboarding.write"))
        }
    }

    fun withUserId(userId: UUID?) = apply { this.userId = userId }

    fun withAccountHolderId(accountHolderId: UUID?) = apply { this.accountHolderId = accountHolderId }

    fun withType(type: String?) = apply { this.type = type }

    fun withTtlMinutes(ttlMinutes: Long?) = apply { this.ttlMinutes = ttlMinutes }

    fun withClientId(clientId: String?) = apply { this.clientId = clientId }

    fun withScopes(scopes: List<String>?) = apply { this.scopes = scopes }

    fun withCognitoGroups(groups: List<String>?) = apply { this.cognitoGroups = groups }

    fun build(): MutableMap<String, Any> {
        val claims: MutableMap<String, Any> = HashMap()
        if (userId != null) {
            claims["sub"] = userId!!.toString()
        } else if (clientId != null) {
            // Cognito client-credentials tokens carry `sub = client_id` so the standard JwtDecoder path works.
            claims["sub"] = clientId!!
        }
        if (accountHolderId != null) {
            claims["account_holder_id"] = accountHolderId!!.toString()
        }
        if (type != null) {
            claims["token_use"] = type!!
        }
        if (ttlMinutes != null) {
            claims["exp"] = convertToDateViaInstant(LocalDateTime.now().plusMinutes(ttlMinutes!!))
        }
        if (clientId != null) {
            claims["client_id"] = clientId!!
        }
        if (scopes != null) {
            claims["scope"] = scopes!!.joinToString(" ")
        }
        if (cognitoGroups != null) {
            claims["cognito:groups"] = cognitoGroups!!
        }

        return claims
    }

    private fun convertToDateViaInstant(dateToConvert: LocalDateTime): Date {
        return Date.from(dateToConvert.atZone(ZoneId.systemDefault()).toInstant())
    }
}
