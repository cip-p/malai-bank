package com.malai.security.cognito

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("malai.cognito.jwt-token")
data class CognitoJwtConfigurationProperties(
    val issuer: String,
    val idExpiresMinutes: Long,
    val refreshExpiresDays: Long,
    val accessExpiresMinutes: Long,
)
