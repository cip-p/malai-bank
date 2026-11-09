package com.malai.security.cognito.model

data class CognitoUpdateUserRequest(
    val accessToken: String,
    val name: String
)
