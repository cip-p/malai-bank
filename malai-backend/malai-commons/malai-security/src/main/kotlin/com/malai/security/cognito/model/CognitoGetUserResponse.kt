package com.malai.security.cognito.model

import java.util.*

data class CognitoGetUserResponse(
    val userId: UUID,
    val name: String,
    val email: String
)
