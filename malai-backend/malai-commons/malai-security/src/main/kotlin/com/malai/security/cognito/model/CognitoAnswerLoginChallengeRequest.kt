package com.malai.security.cognito.model

data class CognitoAnswerLoginChallengeRequest(
    val email: String,
    val confirmationCode: String,
    val session: String
)
