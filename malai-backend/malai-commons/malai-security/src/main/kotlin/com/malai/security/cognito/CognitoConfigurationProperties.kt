package com.malai.security.cognito

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("malai.cognito")
data class CognitoConfigurationProperties(
    val region: String,
    val clientId: String,
    val clientSecret: String,
    val endpointOverride: String? = null
)
