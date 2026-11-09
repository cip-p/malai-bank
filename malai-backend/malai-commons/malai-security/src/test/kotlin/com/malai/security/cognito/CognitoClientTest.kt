package com.malai.security.cognito


import com.malai.commons.exception.InternalServerErrorException
import com.malai.commons.exception.UnauthorizedException
import com.malai.security.cognito.CognitoTestData.testAccessToken
import com.malai.security.cognito.CognitoTestData.testClientId
import com.malai.security.cognito.CognitoTestData.testClientSecret
import com.malai.security.cognito.CognitoTestData.testConfirmationCode
import com.malai.security.cognito.CognitoTestData.testEmail
import com.malai.security.cognito.CognitoTestData.testExpiresIn
import com.malai.security.cognito.CognitoTestData.testIdToken
import com.malai.security.cognito.CognitoTestData.testName
import com.malai.security.cognito.CognitoTestData.testRefreshToken
import com.malai.security.cognito.CognitoTestData.testSecretHashUserId
import com.malai.security.cognito.CognitoTestData.testSecretHashUsername
import com.malai.security.cognito.CognitoTestData.testSession
import com.malai.security.cognito.CognitoTestData.testUserId
import com.malai.security.cognito.model.*
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.BDDMockito.given
import org.mockito.Mockito.*
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.*
import java.util.*

internal class CognitoClientTest {

    private val identityProviderClient = mock(CognitoIdentityProviderClient::class.java)
    private val cognitoClient: CognitoClient = CognitoClient(
        CognitoConfigurationProperties(
            "us-east-1",
            testClientId,
            testClientSecret,
        ),
        identityProviderClient
    )

    @BeforeEach
    fun setup() {
        reset(identityProviderClient)
    }

    @Nested
    inner class RegisterPasswordless {

        @Test
        fun `call Cognito SignUp without a password`() {

            val userSub = UUID.randomUUID()
            `when`(
                identityProviderClient.signUp(
                    SignUpRequest.builder()
                        .userAttributes(
                            listOf(
                                AttributeType.builder()
                                    .name("email")
                                    .value(testEmail)
                                    .build(),
                                AttributeType.builder()
                                    .name("name")
                                    .value(testName)
                                    .build()
                            )
                        )
                        .username(testEmail)
                        .clientId(testClientId)
                        .secretHash(testSecretHashUsername)
                        .build()
                )
            ).thenReturn(
                SignUpResponse.builder()
                    .userSub(userSub.toString())
                    .build()
            )

            val registerResponse = cognitoClient.registerPasswordless(
                CognitoPasswordlessRegisterRequest(testEmail, testName)
            )

            assertThat(registerResponse).isEqualTo(CognitoRegisterResponse(userSub))
            verify(identityProviderClient).signUp(any(SignUpRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            given(identityProviderClient.signUp(any(SignUpRequest::class.java))).willAnswer {
                throw UsernameExistsException.builder().build()
            }

            assertThatThrownBy {
                cognitoClient.registerPasswordless(CognitoPasswordlessRegisterRequest(testEmail, testName))
            }
                .hasMessage("5998")
                .isInstanceOf(UnauthorizedException::class.java)
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            given(identityProviderClient.signUp(any(SignUpRequest::class.java))).willAnswer {
                throw TooManyRequestsException.builder().build()
            }

            assertThatThrownBy {
                cognitoClient.registerPasswordless(CognitoPasswordlessRegisterRequest(testEmail, testName))
            }
                .hasMessage("5998")
                .isInstanceOf(InternalServerErrorException::class.java)
        }
    }

    @Nested
    inner class VerifyRegistration {

        @Test
        fun `confirm the sign up and exchange the returned session for tokens`() {

            `when`(
                identityProviderClient.confirmSignUp(
                    ConfirmSignUpRequest.builder()
                        .username(testEmail)
                        .confirmationCode(testConfirmationCode)
                        .clientId(testClientId)
                        .secretHash(testSecretHashUsername)
                        .build()
                )
            ).thenReturn(
                ConfirmSignUpResponse.builder()
                    .session(testSession)
                    .build()
            )
            `when`(
                identityProviderClient.initiateAuth(
                    InitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.USER_AUTH)
                        .session(testSession)
                        .authParameters(
                            mapOf(
                                "USERNAME" to testEmail,
                                "SECRET_HASH" to testSecretHashUsername
                            )
                        )
                        .clientId(testClientId)
                        .build()
                )
            ).thenReturn(
                InitiateAuthResponse.builder()
                    .authenticationResult {
                        it.accessToken(testAccessToken)
                        it.refreshToken(testRefreshToken)
                        it.idToken(testIdToken)
                        it.expiresIn(testExpiresIn)
                    }
                    .build()
            )

            val tokens = cognitoClient.verifyRegistration(
                CognitoVerifyRegistrationRequest(testEmail, testConfirmationCode)
            )

            assertThat(tokens).isEqualTo(
                CognitoVerificationTokens(
                    testAccessToken,
                    testRefreshToken,
                    testIdToken,
                    testExpiresIn
                )
            )
        }

        @Test
        fun `throw the single generic verification error whatever way Cognito rejects the code`() {

            listOf<Exception>(
                CodeMismatchException.builder().build(),
                ExpiredCodeException.builder().build(),
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                AliasExistsException.builder().build(),
                TooManyFailedAttemptsException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.confirmSignUp(any(ConfirmSignUpRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy {
                    cognitoClient.verifyRegistration(CognitoVerifyRegistrationRequest(testEmail, testConfirmationCode))
                }
                    .hasMessage("5974")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw the single generic verification error when the session exchange is rejected`() {

            `when`(identityProviderClient.confirmSignUp(any(ConfirmSignUpRequest::class.java))).thenReturn(
                ConfirmSignUpResponse.builder()
                    .session(testSession)
                    .build()
            )
            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.initiateAuth(any(InitiateAuthRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy {
                    cognitoClient.verifyRegistration(CognitoVerifyRegistrationRequest(testEmail, testConfirmationCode))
                }
                    .hasMessage("5974")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            given(identityProviderClient.confirmSignUp(any(ConfirmSignUpRequest::class.java))).willAnswer {
                throw TooManyRequestsException.builder().build()
            }

            assertThatThrownBy {
                cognitoClient.verifyRegistration(CognitoVerifyRegistrationRequest(testEmail, testConfirmationCode))
            }
                .hasMessage("5974")
                .isInstanceOf(InternalServerErrorException::class.java)
        }
    }

    @Nested
    inner class StartLoginChallenge {

        @Test
        fun `start an email-OTP login challenge and return the session`() {

            `when`(
                identityProviderClient.initiateAuth(
                    InitiateAuthRequest.builder()
                        .authFlow(AuthFlowType.USER_AUTH)
                        .authParameters(
                            mapOf(
                                "USERNAME" to testEmail,
                                "SECRET_HASH" to testSecretHashUsername,
                                "PREFERRED_CHALLENGE" to "EMAIL_OTP"
                            )
                        )
                        .clientId(testClientId)
                        .build()
                )
            ).thenReturn(
                InitiateAuthResponse.builder()
                    .challengeName(ChallengeNameType.EMAIL_OTP)
                    .session(testSession)
                    .build()
            )

            val challenge = cognitoClient.startLoginChallenge(CognitoStartLoginChallengeRequest(testEmail))

            assertThat(challenge).isEqualTo(CognitoLoginChallenge(testSession))
            verify(identityProviderClient).initiateAuth(any(InitiateAuthRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.initiateAuth(any(InitiateAuthRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.startLoginChallenge(CognitoStartLoginChallengeRequest(testEmail)) }
                    .hasMessage("5995")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.initiateAuth(any(InitiateAuthRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.startLoginChallenge(CognitoStartLoginChallengeRequest(testEmail)) }
                    .hasMessage("5995")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Nested
    inner class AnswerLoginChallenge {

        @Test
        fun `answer the email-OTP login challenge and return the tokens`() {

            `when`(
                identityProviderClient.respondToAuthChallenge(
                    RespondToAuthChallengeRequest.builder()
                        .challengeName(ChallengeNameType.EMAIL_OTP)
                        .session(testSession)
                        .challengeResponses(
                            mapOf(
                                "USERNAME" to testEmail,
                                "EMAIL_OTP_CODE" to testConfirmationCode,
                                "SECRET_HASH" to testSecretHashUsername
                            )
                        )
                        .clientId(testClientId)
                        .build()
                )
            ).thenReturn(
                RespondToAuthChallengeResponse.builder()
                    .authenticationResult {
                        it.accessToken(testAccessToken)
                        it.refreshToken(testRefreshToken)
                        it.idToken(testIdToken)
                        it.expiresIn(testExpiresIn)
                    }
                    .build()
            )

            val tokens = cognitoClient.answerLoginChallenge(
                CognitoAnswerLoginChallengeRequest(testEmail, testConfirmationCode, testSession)
            )

            assertThat(tokens).isEqualTo(
                CognitoVerificationTokens(
                    testAccessToken,
                    testRefreshToken,
                    testIdToken,
                    testExpiresIn
                )
            )
            verify(identityProviderClient).respondToAuthChallenge(any(RespondToAuthChallengeRequest::class.java))
        }

        @Test
        fun `throw the single generic verification error whatever way Cognito rejects the code`() {

            listOf<Exception>(
                CodeMismatchException.builder().build(),
                ExpiredCodeException.builder().build(),
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                TooManyFailedAttemptsException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.respondToAuthChallenge(any(RespondToAuthChallengeRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy {
                    cognitoClient.answerLoginChallenge(
                        CognitoAnswerLoginChallengeRequest(testEmail, testConfirmationCode, testSession)
                    )
                }
                    .hasMessage("5974")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            given(identityProviderClient.respondToAuthChallenge(any(RespondToAuthChallengeRequest::class.java))).willAnswer {
                throw TooManyRequestsException.builder().build()
            }

            assertThatThrownBy {
                cognitoClient.answerLoginChallenge(
                    CognitoAnswerLoginChallengeRequest(testEmail, testConfirmationCode, testSession)
                )
            }
                .hasMessage("5974")
                .isInstanceOf(InternalServerErrorException::class.java)
        }
    }

    @Nested
    inner class RefreshToken {

        @Test
        fun `call Cognito RefreshToken`() {

            val refreshToken = CognitoRefreshTokenRequest(
                testUserId, testRefreshToken
            )
            `when`(
                identityProviderClient.initiateAuth(
                    InitiateAuthRequest.builder()
                        .authParameters(
                            mapOf(
                                "REFRESH_TOKEN" to testRefreshToken,
                                "SECRET_HASH" to testSecretHashUserId
                            )
                        )
                        .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                        .clientId(testClientId)
                        .build()
                )
            ).thenReturn(
                InitiateAuthResponse.builder()
                    .authenticationResult {
                        it.accessToken(testAccessToken)
                        it.idToken(testIdToken)
                        it.expiresIn(testExpiresIn)
                    }
                    .build()
            )

            val refreshTokenResponse = cognitoClient.refreshToken(refreshToken)

            assertThat(refreshTokenResponse).isEqualTo(
                CognitoRefreshTokenResponse(
                    testAccessToken,
                    testRefreshToken,
                    testIdToken,
                    testExpiresIn
                )
            )
            verify(identityProviderClient).initiateAuth(any(InitiateAuthRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            val refreshToken = CognitoRefreshTokenRequest(
                testUserId, testRefreshToken
            )
            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                UserNotConfirmedException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.initiateAuth(any(InitiateAuthRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.refreshToken(refreshToken) }
                    .hasMessage("5994")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            val refreshToken = CognitoRefreshTokenRequest(
                testUserId, testRefreshToken
            )
            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.initiateAuth(any(InitiateAuthRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.refreshToken(refreshToken) }
                    .hasMessage("5994")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Nested
    inner class ResendConfirmationCode {

        @Test
        fun `call Cognito ResendConfirmationCode`() {

            val resendConfirmationCode = CognitoResendConfirmationCodeRequest(
                testEmail
            )
            `when`(
                identityProviderClient.resendConfirmationCode(
                    ResendConfirmationCodeRequest.builder()
                        .username(testEmail)
                        .clientId(testClientId)
                        .secretHash(testSecretHashUsername)
                        .build()
                )
            ).thenReturn(
                ResendConfirmationCodeResponse.builder()
                    .build()
            )

            cognitoClient.resendConfirmationCode(resendConfirmationCode)

            verify(identityProviderClient).resendConfirmationCode(any(ResendConfirmationCodeRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            val resendConfirmationCode = CognitoResendConfirmationCodeRequest(
                testEmail
            )
            listOf<Exception>(
                UserNotFoundException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.resendConfirmationCode(any(ResendConfirmationCodeRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.resendConfirmationCode(resendConfirmationCode) }
                    .hasMessage("5996")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            val resendConfirmationCode = CognitoResendConfirmationCodeRequest(
                testEmail
            )
            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.resendConfirmationCode(any(ResendConfirmationCodeRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.resendConfirmationCode(resendConfirmationCode) }
                    .hasMessage("5996")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Nested
    inner class SignOut {

        @Test
        fun `call Cognito SignOut`() {

            val signOut = CognitoSignOutRequest(
                testAccessToken
            )
            `when`(
                identityProviderClient.globalSignOut(
                    GlobalSignOutRequest.builder()
                        .accessToken(testAccessToken)
                        .build()
                )
            ).thenReturn(
                GlobalSignOutResponse.builder()
                    .build()
            )

            cognitoClient.signOut(signOut)

            verify(identityProviderClient).globalSignOut(any(GlobalSignOutRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            val signOut = CognitoSignOutRequest(
                testAccessToken
            )
            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.globalSignOut(any(GlobalSignOutRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.signOut(signOut) }
                    .hasMessage("5990")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            val signOut = CognitoSignOutRequest(
                testAccessToken
            )
            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.globalSignOut(any(GlobalSignOutRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.signOut(signOut) }
                    .hasMessage("5990")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Nested
    inner class GetUser {

        @Test
        fun `call Cognito GetUser`() {

            val getUser = CognitoGetUserRequest(
                testAccessToken
            )
            `when`(
                identityProviderClient.getUser(
                    GetUserRequest.builder()
                        .accessToken(testAccessToken)
                        .build()
                )
            ).thenReturn(
                GetUserResponse.builder()
                    .userAttributes(
                        listOf(
                            AttributeType.builder()
                                .name("sub")
                                .value(testUserId.toString())
                                .build(),
                            AttributeType.builder()
                                .name("name")
                                .value(testName)
                                .build(),
                            AttributeType.builder()
                                .name("email")
                                .value(testEmail)
                                .build()
                        )
                    )
                    .build()
            )

            val user = cognitoClient.getUser(getUser)
            assertThat(user).isEqualTo(
                CognitoGetUserResponse(testUserId, testName, testEmail)
            )

            verify(identityProviderClient).getUser(any(GetUserRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            val getUser = CognitoGetUserRequest(
                testAccessToken
            )
            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.getUser(any(GetUserRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.getUser(getUser) }
                    .hasMessage("5989")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            val getUser = CognitoGetUserRequest(
                testAccessToken
            )
            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.getUser(any(GetUserRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.getUser(getUser) }
                    .hasMessage("5989")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Nested
    inner class UpdateUser {

        @Test
        fun `call Cognito UpdateUser`() {

            val updateUser = CognitoUpdateUserRequest(
                testAccessToken,
                testName
            )
            `when`(
                identityProviderClient.updateUserAttributes(
                    UpdateUserAttributesRequest.builder()
                        .accessToken(testAccessToken)
                        .userAttributes(
                            AttributeType.builder()
                                .name("name")
                                .value(testName)
                                .build()
                        )
                        .build()
                )
            ).thenReturn(
                UpdateUserAttributesResponse.builder().build()
            )

            cognitoClient.updateUser(updateUser)

            verify(identityProviderClient).updateUserAttributes(any(UpdateUserAttributesRequest::class.java))
        }

        @Test
        fun `throw UnauthorizedException for certain Cognito errors`() {

            val updateUser = CognitoUpdateUserRequest(
                testAccessToken,
                testName
            )
            listOf<Exception>(
                UserNotFoundException.builder().build(),
                NotAuthorizedException.builder().build(),
                UserNotConfirmedException.builder().build(),
            ).forEach { exception ->
                given(identityProviderClient.updateUserAttributes(any(UpdateUserAttributesRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.updateUser(updateUser) }
                    .hasMessage("5988")
                    .isInstanceOf(UnauthorizedException::class.java)
            }
        }

        @Test
        fun `throw InternalServerErrorException for unhandled Cognito errors`() {

            val updateUser = CognitoUpdateUserRequest(
                testAccessToken,
                testName
            )
            listOf<Exception>(
                java.lang.Exception("test exception"),
                TooManyRequestsException.builder().build()
            ).forEach { exception ->
                given(identityProviderClient.updateUserAttributes(any(UpdateUserAttributesRequest::class.java))).willAnswer {
                    throw exception
                }

                assertThatThrownBy { cognitoClient.updateUser(updateUser) }
                    .hasMessage("5988")
                    .isInstanceOf(InternalServerErrorException::class.java)
            }
        }
    }

    @Test
    fun `calculate secret hash`() {

        val secretHash = cognitoClient.calculateSecretHash(testEmail)

        assertThat(secretHash).isEqualTo(testSecretHashUsername)
    }

}
