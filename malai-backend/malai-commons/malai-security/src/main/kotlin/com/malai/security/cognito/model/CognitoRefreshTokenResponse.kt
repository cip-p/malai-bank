package com.malai.security.cognito.model

data class CognitoRefreshTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val idToken: String,
    val expiresIn: Int
)
