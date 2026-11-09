package com.malai.security.cognito.model

data class CognitoVerifyRegistrationRequest(
    val email: String,
    val confirmationCode: String
)
