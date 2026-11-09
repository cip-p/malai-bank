package com.malai.auth.support

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.acceptance.WithWiremock
import com.malai.test.acceptance.kensa.testingObjectMapper
import dev.kensa.Action
import dev.kensa.GivensContext
import java.util.*

interface WithCognito : WithWiremock {

    fun cognitoRegisterReturnsSuccessfulForUserId(userId: UUID) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.SignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "UserSub" to userId.toString(),
                                    "UserConfirmed" to false
                                )
                            )
                        )
                )
        )
    }

    fun cognitoAdminAddUserToGroupReturnsSuccessful() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.AdminAddUserToGroup"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    fun cognitoAdminAddUserToGroupReturnsServerError() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.AdminAddUserToGroup"))
                .willReturn(
                    aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "__type" to "InternalErrorException",
                                    "message" to "boom",
                                )
                            )
                        )
                )
        )
    }

    fun cognitoConfirmSignUpReturnsSession(session: String) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ConfirmSignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "Session" to session
                                )
                            )
                        )
                )
        )
    }

    fun cognitoConfirmSignUpReturnsCodeMismatch() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ConfirmSignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "__type" to "CodeMismatchException",
                                    "message" to "Invalid verification code provided, please try again.",
                                )
                            )
                        )
                )
        )
    }

    fun cognitoConfirmSignUpReturnsExpiredCode() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ConfirmSignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "__type" to "ExpiredCodeException",
                                    "message" to "Invalid code provided, please request a code again.",
                                )
                            )
                        )
                )
        )
    }

    fun cognitoVerificationSessionExchangeReturns(
        session: String,
        idToken: String = "some-cognito-id-token",
        accessToken: String = "some-cognito-access-token",
        refreshToken: String = "some-cognito-refresh-token",
        expiresIn: Int = 3600,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.InitiateAuth"))
                .withRequestBody(matchingJsonPath("$.AuthFlow", equalTo("USER_AUTH")))
                .withRequestBody(matchingJsonPath("$.Session", equalTo(session)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "AuthenticationResult" to mapOf(
                                        "IdToken" to idToken,
                                        "AccessToken" to accessToken,
                                        "RefreshToken" to refreshToken,
                                        "ExpiresIn" to expiresIn,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoStartLoginChallengeReturnsSession(session: String) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.InitiateAuth"))
                .withRequestBody(matchingJsonPath("$.AuthParameters.PREFERRED_CHALLENGE", equalTo("EMAIL_OTP")))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "ChallengeName" to "EMAIL_OTP",
                                    "Session" to session
                                )
                            )
                        )
                )
        )
    }

    fun cognitoAnswerLoginChallengeReturns(
        session: String,
        idToken: String = "some-cognito-id-token",
        accessToken: String = "some-cognito-access-token",
        refreshToken: String = "some-cognito-refresh-token",
        expiresIn: Int = 3600,
    ) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.RespondToAuthChallenge"))
                .withRequestBody(matchingJsonPath("$.ChallengeName", equalTo("EMAIL_OTP")))
                .withRequestBody(matchingJsonPath("$.Session", equalTo(session)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "AuthenticationResult" to mapOf(
                                        "IdToken" to idToken,
                                        "AccessToken" to accessToken,
                                        "RefreshToken" to refreshToken,
                                        "ExpiresIn" to expiresIn,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoAnswerLoginChallengeReturnsCodeMismatch() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.RespondToAuthChallenge"))
                .willReturn(
                    aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "__type" to "CodeMismatchException",
                                    "message" to "Invalid code provided, please try again.",
                                )
                            )
                        )
                )
        )
    }

    fun cognitoRefreshTokenReturns(
        accessToken: String = "some-new-cognito-access-token",
        idToken: String = "some-new-cognito-id-token",
        expiresIn: Int = 3600
    ) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.InitiateAuth"))
                .withRequestBody(matchingJsonPath("$.AuthFlow", equalTo("REFRESH_TOKEN_AUTH")))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "AuthenticationResult" to mapOf(
                                        "IdToken" to idToken,
                                        "AccessToken" to accessToken,
                                        "ExpiresIn" to expiresIn,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoResendConfirmationCodeReturnsSuccessful() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ResendConfirmationCode"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "CodeDeliveryDetails" to mapOf(
                                        "AttributeName" to "email",
                                        "DeliveryMedium" to "EMAIL",
                                        "Destination" to "s***@e***.com"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoGetUserReturns(userId: UUID, email: String, name: String) = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.GetUser"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "Username" to userId.toString(),
                                    "UserAttributes" to listOf(
                                        mapOf("Name" to "sub", "Value" to userId.toString()),
                                        mapOf("Name" to "name", "Value" to name),
                                        mapOf("Name" to "email", "Value" to email)
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoUpdateUserAttributesReturnsSuccessful() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.UpdateUserAttributes"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    fun cognitoSignOutReturnsSuccessful() = Action<GivensContext> {
        wiremock().stubFor(
            post(urlPathMatching("$CONTEXT_PATH/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.GlobalSignOut"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    companion object {
        const val CONTEXT_PATH = "/cognito"
    }

}
