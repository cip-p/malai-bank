package com.malai.security.cognito.model

import java.util.*

data class CognitoRefreshTokenRequest(
    val userId: UUID,
    val refreshToken: String
)
