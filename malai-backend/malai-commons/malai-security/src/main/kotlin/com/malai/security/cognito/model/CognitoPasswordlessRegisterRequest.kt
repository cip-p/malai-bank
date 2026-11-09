package com.malai.security.cognito.model

data class CognitoPasswordlessRegisterRequest(
    val email: String,
    val name: String
)
