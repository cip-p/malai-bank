package com.malai.test.e2e

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.malai.test.acceptance.WithIdentityProvider
import com.malai.test.acceptance.WithCognitoJwtToken
import com.malai.test.acceptance.WithServiceJwtToken
import com.malai.test.acceptance.WithWiremock
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.MalaiE2ETestContext.OAUTH2_SERVICE_PROXY_PATH
import java.util.*

/**
 * Stubs the AWS Cognito SDK endpoints on the shared e2e WireMock. Each Cognito SDK call is a
 * `POST /` with an `X-Amz-Target` header identifying the operation; the stubs match on that
 * header.
 *
 * JWKS handling and ID-token generation come from [WithIdentityProvider] and [WithCognitoJwtToken]
 * respectively. [WithServiceJwtToken] adds service (`client_credentials`) JWT minting so the
 * register orchestrator can authenticate to account-holder-service / account-service.
 */
interface WithCognito : WithWiremock, WithIdentityProvider, WithCognitoJwtToken, WithServiceJwtToken {

    fun cognitoCanRegisterUser(email: String, userId: UUID) = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.SignUp"))
                .withRequestBody(matchingJsonPath("$.Username", equalTo(email)))
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
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.AdminAddUserToGroup"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ConfirmSignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    fun cognitoCanVerifyRegistration(userId: UUID) = CallableAction {
        val accessToken = generateAccessTokenForUserId(userId)
        val refreshToken = generateRefreshTokenForUserId(userId)
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.ConfirmSignUp"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf("Session" to "e2e-verification-session")
                            )
                        )
                )
        )
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.InitiateAuth"))
                .withRequestBody(matchingJsonPath("$.Session", equalTo("e2e-verification-session")))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "AuthenticationResult" to mapOf(
                                        "IdToken" to accessToken,
                                        "AccessToken" to accessToken,
                                        "RefreshToken" to refreshToken,
                                        "ExpiresIn" to 3600,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoCanLogInUser(email: String, userId: UUID) = CallableAction {
        val idToken = generateAccessTokenForUserId(userId)
        val refreshToken = generateRefreshTokenForUserId(userId)
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.InitiateAuth"))
                .withRequestBody(matchingJsonPath("$.AuthParameters.USERNAME", equalTo(email)))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody(
                            testingObjectMapper.writeValueAsString(
                                mapOf(
                                    "AuthenticationResult" to mapOf(
                                        "IdToken" to idToken,
                                        "AccessToken" to idToken,
                                        "RefreshToken" to refreshToken,
                                        "ExpiresIn" to 3600,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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
                                        mapOf("Name" to "email", "Value" to email)
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoCanStartLoginChallenge(session: String) = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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

    fun cognitoCanAnswerLoginChallenge(userId: UUID, session: String) = CallableAction {
        val accessToken = generateAccessTokenForUserId(userId)
        val refreshToken = generateRefreshTokenForUserId(userId)
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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
                                        "IdToken" to accessToken,
                                        "AccessToken" to accessToken,
                                        "RefreshToken" to refreshToken,
                                        "ExpiresIn" to 3600,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoCanResendConfirmationCode() = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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

    fun cognitoCanRefreshToken(userId: UUID) = CallableAction {
        val idToken = generateAccessTokenForUserId(userId)
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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
                                        "AccessToken" to idToken,
                                        "ExpiresIn" to 3600,
                                        "TokenType" to "Bearer"
                                    )
                                )
                            )
                        )
                )
        )
    }

    fun cognitoCanSignOut() = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.GlobalSignOut"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    fun cognitoCanGetUser(email: String, userId: UUID, name: String) = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
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

    fun cognitoCanUpdateUserAttributes() = CallableAction {
        wiremock().stubFor(
            post(urlPathMatching("/?"))
                .withHeader("X-Amz-Target", equalTo("AWSCognitoIdentityProviderService.UpdateUserAttributes"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/x-amz-json-1.1")
                        .withBody("{}")
                )
        )
    }

    /**
     * Stubs the Cognito service (`client_credentials`) token endpoint at
     * `${COGNITO_SERVICE_PROXY_PATH}/oauth2/token`. The token returned is a real JWT signed by
     * the stubbed Cognito signer (so the receiving services accept it via their JWKS check)
     * with `client_id` + `scope` claims set so `ServiceJwtFilter` validates it.
     *
     * Deliberately *excluded* from the Kensa sequence diagram by [MalaiE2ETestContext.boot] —
     * this is plumbing, not a business interaction.
     */
    fun cognitoCanIssueServiceToken(
        clientId: String = "test-service-client-id",
        scopes: List<String> = listOf("malai-internal/onboarding.write"),
        expiresInSeconds: Int = 3600,
    ) = CallableAction {
        val token = generateServiceAccessToken(clientId, scopes)
        wiremock().stubFor(
            post(urlPathEqualTo("$OAUTH2_SERVICE_PROXY_PATH/oauth2/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            testingObjectMapper.writeValueAsString(mapOf(
                                "access_token" to token,
                                "expires_in" to expiresInSeconds,
                                "token_type" to "Bearer",
                            ))
                        )
                )
        )
    }
}
