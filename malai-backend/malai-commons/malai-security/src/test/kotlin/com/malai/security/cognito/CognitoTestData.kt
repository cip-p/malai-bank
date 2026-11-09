package com.malai.security.cognito

import com.malai.security.cognito.model.CognitoGetUserResponse
import com.malai.security.cognito.model.CognitoRefreshTokenResponse
import java.util.*
import java.util.UUID.randomUUID

object CognitoTestData {

    const val testEmail = "test_user_name@example.com"
    const val testSuperUserEmail = "test_super_user_name@example.com"
    const val testName = "Test Name"
    const val testSuperUserName = "Test Super Name"

    @JvmField
    val testUserId: UUID = UUID.fromString("9537728a-e903-459b-b92b-76b77d766bb6")
    const val testClientId = "test-client-id"
    const val testClientSecret = "test-client-secret"
    const val testConfirmationCode = "1234321"
    const val testSession = "test-verification-session"
    const val testAccessToken = "test-access-token"
    const val testRefreshToken = "test-refresh-token"
    const val testIdToken = "test-id-token"

    @JvmField
    val testAuthToken: UUID = randomUUID()
    const val testExpiresIn = 1234
    const val testSecretHashUsername = "DkfIR77Al5NTxGEpZQ4PNKMNJONp9CSldEHUm4h9Rp0="
    const val testSecretHashUserId = "FWJSabOxOz4Os0WCM1ogEC964D+z3JaYoFESvbmvjVk="


    @JvmField
    val testCognitoRefreshTokenResponse =
        CognitoRefreshTokenResponse(testAccessToken, testRefreshToken, testIdToken, testExpiresIn)

    @JvmField
    val testCognitoGetUserResponse = CognitoGetUserResponse(testUserId, testName, testEmail)
}
