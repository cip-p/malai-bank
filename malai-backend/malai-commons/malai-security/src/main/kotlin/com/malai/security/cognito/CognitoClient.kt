package com.malai.security.cognito

import com.malai.commons.exception.CognitoUnauthorizedException
import com.malai.commons.exception.InternalServerErrorException
import com.malai.commons.exception.MalaiCode.*
import com.malai.security.cognito.model.*
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.*
import java.lang.System.currentTimeMillis
import java.nio.charset.StandardCharsets
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec


@Service
class CognitoClient(
    private val cognitoProperties: CognitoConfigurationProperties,
    private val identityProviderClient: CognitoIdentityProviderClient
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PreDestroy
    fun preDestroy() {
        identityProviderClient.close()
    }

    fun registerPasswordless(register: CognitoPasswordlessRegisterRequest): CognitoRegisterResponse {
        val email = register.email

        val attrs: List<AttributeType> = listOf(
            AttributeType.builder()
                .name("email")
                .value(email)
                .build(),
            AttributeType.builder()
                .name("name")
                .value(register.name)
                .build()
        )

        val signUpRequest = SignUpRequest.builder()
            .userAttributes(attrs)
            .username(email)
            .clientId(cognitoProperties.clientId)
            .secretHash(calculateSecretHash(email))
            .build()

        log.debug("calling Cognito passwordless SignUp")
        val start = currentTimeMillis()

        val signUpResponse = cognitoSignUp(signUpRequest)

        log.debug("received Cognito passwordless SignUp response: $signUpResponse, took ${currentTimeMillis() - start} ms")

        val userId = UUID.fromString(signUpResponse.userSub())
        log.info("user registered passwordless, $email, user id $userId")

        return CognitoRegisterResponse(userId)
    }

    fun verifyRegistration(verifyRegistration: CognitoVerifyRegistrationRequest): CognitoVerificationTokens {
        val email = verifyRegistration.email

        val confirmSignUpRequest = ConfirmSignUpRequest.builder()
            .username(email)
            .confirmationCode(verifyRegistration.confirmationCode)
            .clientId(cognitoProperties.clientId)
            .secretHash(calculateSecretHash(email))
            .build()

        log.debug("calling Cognito ConfirmSignUp for verification")
        val start = currentTimeMillis()

        val session = cognitoVerificationConfirmSignUp(confirmSignUpRequest).session()

        val sessionExchangeRequest = InitiateAuthRequest.builder()
            .authFlow(AuthFlowType.USER_AUTH)
            .session(session)
            .authParameters(
                mapOf(
                    "USERNAME" to email,
                    "SECRET_HASH" to calculateSecretHash(email)
                )
            )
            .clientId(cognitoProperties.clientId)
            .build()

        log.debug("calling Cognito InitiateAuth(USER_AUTH) to exchange the verification session")

        val authenticationResult = cognitoVerificationSessionExchange(sessionExchangeRequest).authenticationResult()

        log.debug("verification session exchange complete, took ${currentTimeMillis() - start} ms")
        log.info("user verified registration, $email")

        return CognitoVerificationTokens(
            authenticationResult.accessToken(),
            authenticationResult.refreshToken(),
            authenticationResult.idToken(),
            authenticationResult.expiresIn()
        )
    }

    fun startLoginChallenge(startLoginChallenge: CognitoStartLoginChallengeRequest): CognitoLoginChallenge {
        val email = startLoginChallenge.email

        val initiateAuthRequest = InitiateAuthRequest.builder()
            .authFlow(AuthFlowType.USER_AUTH)
            .authParameters(
                mapOf(
                    "USERNAME" to email,
                    "SECRET_HASH" to calculateSecretHash(email),
                    "PREFERRED_CHALLENGE" to "EMAIL_OTP"
                )
            )
            .clientId(cognitoProperties.clientId)
            .build()

        log.debug("calling Cognito InitiateAuth(USER_AUTH) to start an email-OTP login challenge")
        val start = currentTimeMillis()

        val session = cognitoStartLoginChallenge(initiateAuthRequest).session()

        log.debug("received Cognito login challenge, took ${currentTimeMillis() - start} ms")
        log.info("login challenge started, $email")

        return CognitoLoginChallenge(session)
    }

    fun answerLoginChallenge(answerLoginChallenge: CognitoAnswerLoginChallengeRequest): CognitoVerificationTokens {
        val email = answerLoginChallenge.email

        val respondToAuthChallengeRequest = RespondToAuthChallengeRequest.builder()
            .challengeName(ChallengeNameType.EMAIL_OTP)
            .session(answerLoginChallenge.session)
            .challengeResponses(
                mapOf(
                    "USERNAME" to email,
                    "EMAIL_OTP_CODE" to answerLoginChallenge.confirmationCode,
                    "SECRET_HASH" to calculateSecretHash(email)
                )
            )
            .clientId(cognitoProperties.clientId)
            .build()

        log.debug("calling Cognito RespondToAuthChallenge(EMAIL_OTP) to answer the login challenge")
        val start = currentTimeMillis()

        val authenticationResult = cognitoAnswerLoginChallenge(respondToAuthChallengeRequest).authenticationResult()

        log.debug("login challenge answered, took ${currentTimeMillis() - start} ms")
        log.info("user verified login, $email")

        return CognitoVerificationTokens(
            authenticationResult.accessToken(),
            authenticationResult.refreshToken(),
            authenticationResult.idToken(),
            authenticationResult.expiresIn()
        )
    }

    fun refreshToken(cognitoRefreshTokenRequest: CognitoRefreshTokenRequest): CognitoRefreshTokenResponse {
        val userId = cognitoRefreshTokenRequest.userId
        val refreshToken = cognitoRefreshTokenRequest.refreshToken

        val authParameters = mapOf(
            "REFRESH_TOKEN" to refreshToken,
            "SECRET_HASH" to calculateSecretHash(userId.toString())
        )

        val refreshTokenRequest = InitiateAuthRequest.builder()
            .authParameters(authParameters)
            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
            .clientId(cognitoProperties.clientId)
            .build()

        log.debug("calling Cognito refreshToken(InitAuth)")
        val start = currentTimeMillis()

        val refreshTokenResponse = cognitoRefreshToken(refreshTokenRequest)

        log.debug("received Cognito refreshToken(InitAuth) response: $refreshTokenResponse, took ${currentTimeMillis() - start} ms")

        return CognitoRefreshTokenResponse(
            refreshTokenResponse.authenticationResult().accessToken(),
            refreshToken, // the refresh token is not returned for this operation
            refreshTokenResponse.authenticationResult().idToken(),
            refreshTokenResponse.authenticationResult().expiresIn()
        )
    }

    fun resendConfirmationCode(cognitoResendConfirmationCodeRequest: CognitoResendConfirmationCodeRequest) {
        val username = cognitoResendConfirmationCodeRequest.username

        val resendConfirmationCodeRequest = ResendConfirmationCodeRequest.builder()
            .username(username)
            .clientId(cognitoProperties.clientId)
            .secretHash(calculateSecretHash(username))
            .build()

        log.debug("calling Cognito ResendConfirmationCode")
        val start = currentTimeMillis()

        val resendConfirmationCodeResponse = cognitoResendConfirmationCode(resendConfirmationCodeRequest)

        log.debug("received Cognito ResendConfirmationCode response: $resendConfirmationCodeResponse, took ${currentTimeMillis() - start} ms")
    }

    fun signOut(cognitoSignOutRequest: CognitoSignOutRequest) {
        val accessToken = cognitoSignOutRequest.accessToken

        val signOutRequest = GlobalSignOutRequest.builder()
            .accessToken(accessToken)
            .build()

        log.debug("calling Cognito GlobalSignOut")
        val start = currentTimeMillis()

        val signOutResponse = cognitoSignOut(signOutRequest)

        log.debug("received Cognito GlobalSignOut response: $signOutResponse, took ${currentTimeMillis() - start} ms")
    }

    fun getUser(cognitoGetUserRequest: CognitoGetUserRequest): CognitoGetUserResponse {
        val accessToken = cognitoGetUserRequest.accessToken

        val getUserRequest = GetUserRequest.builder()
            .accessToken(accessToken)
            .build()

        log.debug("calling Cognito GetUser")
        val start = currentTimeMillis()

        val getUserResponse = cognitoGetUser(getUserRequest)

        log.debug("received Cognito GetUser response: $getUserResponse, took ${currentTimeMillis() - start} ms")

        val userId = userAttribute(getUserResponse, "sub")
        val name = userAttribute(getUserResponse, "name")
        val email = userAttribute(getUserResponse, "email")

        return CognitoGetUserResponse(UUID.fromString(userId), name, email)
    }

    fun updateUser(cognitoUpdateUserRequest: CognitoUpdateUserRequest) {
        val accessToken = cognitoUpdateUserRequest.accessToken

        val updateUserAttributesRequest = UpdateUserAttributesRequest.builder()
            .accessToken(accessToken)
            .userAttributes(
                AttributeType.builder()
                    .name("name")
                    .value(cognitoUpdateUserRequest.name)
                    .build()
            )
            .build()

        log.debug("calling Cognito UpdateUserAttributes")
        val start = currentTimeMillis()

        val updateUserAttributesResponse = cognitoUpdateUser(updateUserAttributesRequest)

        log.debug("received Cognito UpdateUserAttributes response: $updateUserAttributesResponse, took ${currentTimeMillis() - start} ms")
    }

    private fun userAttribute(getUserResponse: GetUserResponse, attributeName: String) =
        getUserResponse.userAttributes().first { it.name() == attributeName }.value()

    private fun cognitoSignUp(signUpRequest: SignUpRequest): SignUpResponse {
        try {
            return identityProviderClient.signUp(signUpRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UsernameExistsException -> {
                    log.error("Cognito sign up error, username ${signUpRequest.username()}", ex)
                    throw CognitoUnauthorizedException(COGNITO_REGISTER_ERROR)
                }

                else -> {
                    log.error("Cognito unhandled sign up error, username ${signUpRequest.username()}", ex)
                    throw InternalServerErrorException(COGNITO_REGISTER_ERROR)
                }
            }
        }
    }

    private fun cognitoVerificationConfirmSignUp(confirmSignUpRequest: ConfirmSignUpRequest): ConfirmSignUpResponse {
        try {
            return identityProviderClient.confirmSignUp(confirmSignUpRequest)
        } catch (ex: Exception) {
            throw verificationError(ex, "confirm sign up", confirmSignUpRequest.username())
        }
    }

    private fun cognitoVerificationSessionExchange(sessionExchangeRequest: InitiateAuthRequest): InitiateAuthResponse {
        try {
            return identityProviderClient.initiateAuth(sessionExchangeRequest)
        } catch (ex: Exception) {
            throw verificationError(ex, "session exchange", sessionExchangeRequest.authParameters()["USERNAME"])
        }
    }

    private fun verificationError(ex: Exception, operation: String, username: String?): Exception =
        when (ex) {
            is CodeMismatchException,
            is ExpiredCodeException,
            is UserNotFoundException,
            is NotAuthorizedException,
            is AliasExistsException,
            is TooManyFailedAttemptsException,
            is UserNotConfirmedException -> {
                log.warn("Cognito verification rejected at $operation, $username", ex)
                CognitoUnauthorizedException(VERIFICATION_FAILED)
            }

            else -> {
                log.error("Cognito unhandled verification error at $operation, $username", ex)
                InternalServerErrorException(VERIFICATION_FAILED)
            }
        }

    private fun cognitoStartLoginChallenge(initiateAuthRequest: InitiateAuthRequest): InitiateAuthResponse {
        try {
            return identityProviderClient.initiateAuth(initiateAuthRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException,
                is NotAuthorizedException,
                is UserNotConfirmedException -> {
                    log.error(
                        "Cognito start login challenge error, username ${initiateAuthRequest.authParameters()["USERNAME"]}", ex)
                    throw CognitoUnauthorizedException(COGNITO_LOGIN_ERROR)
                }

                else -> {
                    log.error(
                        "Cognito unhandled start login challenge error, username ${initiateAuthRequest.authParameters()["USERNAME"]}", ex)
                    throw InternalServerErrorException(COGNITO_LOGIN_ERROR)
                }
            }
        }
    }

    private fun cognitoAnswerLoginChallenge(respondToAuthChallengeRequest: RespondToAuthChallengeRequest): RespondToAuthChallengeResponse {
        try {
            return identityProviderClient.respondToAuthChallenge(respondToAuthChallengeRequest)
        } catch (ex: Exception) {
            throw verificationError(ex, "answer login challenge", respondToAuthChallengeRequest.challengeResponses()["USERNAME"])
        }
    }

    private fun cognitoResendConfirmationCode(resendConfirmationCodeRequest: ResendConfirmationCodeRequest): ResendConfirmationCodeResponse {
        try {
            return identityProviderClient.resendConfirmationCode(resendConfirmationCodeRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException -> {
                    log.error(
                        "Cognito resend confirmation code error, username ${resendConfirmationCodeRequest.username()}", ex)
                    throw CognitoUnauthorizedException(COGNITO_RESEND_CONFIRMATION_CODE_ERROR)
                }

                else -> {
                    log.error(
                        "Cognito unhandled resend confirmation code error, username ${resendConfirmationCodeRequest.username()}", ex)
                    throw InternalServerErrorException(COGNITO_RESEND_CONFIRMATION_CODE_ERROR)
                }
            }
        }
    }

    private fun cognitoRefreshToken(refreshTokenRequest: InitiateAuthRequest): InitiateAuthResponse {
        try {
            return identityProviderClient.initiateAuth(refreshTokenRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException,
                is NotAuthorizedException,
                is UserNotConfirmedException -> {
                    log.error("Cognito refresh token error", ex)
                    throw CognitoUnauthorizedException(COGNITO_REFRESH_TOKEN_ERROR)
                }

                else -> {
                    log.error("Cognito unhandled refresh token error", ex)
                    throw InternalServerErrorException(COGNITO_REFRESH_TOKEN_ERROR)
                }
            }
        }
    }

    private fun cognitoSignOut(signOutRequest: GlobalSignOutRequest): GlobalSignOutResponse {
        try {
            return identityProviderClient.globalSignOut(signOutRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException,
                is NotAuthorizedException,
                is UserNotConfirmedException -> {
                    log.error("Cognito signout error", ex)
                    throw CognitoUnauthorizedException(COGNITO_SIGN_OUT_ERROR)
                }

                else -> {
                    log.error("Cognito unhandled signout error", ex)
                    throw InternalServerErrorException(COGNITO_SIGN_OUT_ERROR)
                }
            }
        }
    }

    private fun cognitoGetUser(getUserRequest: GetUserRequest): GetUserResponse {
        try {
            return identityProviderClient.getUser(getUserRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException,
                is NotAuthorizedException,
                is UserNotConfirmedException -> {
                    log.error("Cognito get user error", ex)
                    throw CognitoUnauthorizedException(COGNITO_GET_USER_ERROR)
                }

                else -> {
                    log.error("Cognito unhandled get user error", ex)
                    throw InternalServerErrorException(COGNITO_GET_USER_ERROR)
                }
            }
        }
    }

    private fun cognitoUpdateUser(updateUserRequest: UpdateUserAttributesRequest): UpdateUserAttributesResponse {
        try {
            return identityProviderClient.updateUserAttributes(updateUserRequest)
        } catch (ex: Exception) {
            when (ex) {
                is UserNotFoundException,
                is NotAuthorizedException,
                is UserNotConfirmedException -> {
                    log.error("Cognito update user error", ex)
                    throw CognitoUnauthorizedException(COGNITO_UPDATE_USER_ERROR)
                }

                else -> {
                    log.error("Cognito unhandled update user error", ex)
                    throw InternalServerErrorException(COGNITO_UPDATE_USER_ERROR)
                }
            }
        }
    }

    fun calculateSecretHash(userName: String): String {
        val HMAC_SHA256_ALGORITHM = "HmacSHA256"
        val signingKey = SecretKeySpec(
            cognitoProperties.clientSecret.toByteArray(StandardCharsets.UTF_8),
            HMAC_SHA256_ALGORITHM
        )
        return try {
            Mac.getInstance(HMAC_SHA256_ALGORITHM)
                .apply {
                    init(signingKey)
                    update(userName.toByteArray(StandardCharsets.UTF_8))
                }
                .doFinal(cognitoProperties.clientId.toByteArray(StandardCharsets.UTF_8))
                .let {
                    Base64.getEncoder().encodeToString(it)
                }
        } catch (e: Exception) {
            throw RuntimeException("Error while calculating secret hash", e)
        }
    }

}
