package com.malai.auth.service

import com.malai.auth.entity.UserEntity
import com.malai.auth.model.*
import com.malai.commons.exception.CognitoUnauthorizedException
import com.malai.commons.exception.MalaiCode.VERIFICATION_FAILED
import com.malai.security.audit.ActorType.CUSTOMER
import com.malai.security.audit.ActorType.SYSTEM
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.security.cognito.CognitoClient
import com.malai.security.cognito.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

@Service
class AuthService(
    private val userService: UserService,
    private val cognitoClient: CognitoClient,
    private val registrationOrchestrator: RegistrationOrchestrator,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun register(register: RegisterRequest): String {
        val email = register.email!!

        val existing = userService.findUserByEmail(email)
        if (existing != null) {
            return continueForExistingUser(email, existing)
        }

        val userId = try {
            registrationOrchestrator.register(register)
        } catch (e: Exception) {
            recordFailed("REGISTER_FAILED", attemptedEmail = email, reason = e.javaClass.simpleName)
            throw e
        }

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = CUSTOMER,
                action = "REGISTERED",
                resourceType = "User",
                resourceId = userId.toString(),
            )
        )
        return fabricatedChallengeSession()
    }

    fun verifyCode(verifyCodeRequest: VerifyCodeRequest, challengeSession: String?): LoginResponse {
        val email = verifyCodeRequest.email!!

        val response = try {
            val user = userService.findUserByEmail(email)
                ?: throw CognitoUnauthorizedException(VERIFICATION_FAILED)

            val tokens = if (user.registrationConfirmed) {
                val session = challengeSession ?: throw CognitoUnauthorizedException(VERIFICATION_FAILED)
                cognitoClient.answerLoginChallenge(
                    CognitoAnswerLoginChallengeRequest(email, verifyCodeRequest.confirmationCode!!, session)
                )
            } else {
                registrationOrchestrator.completeRegistration(email, verifyCodeRequest.confirmationCode!!)
            }

            LoginResponse(
                userId = user.id,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                idToken = tokens.idToken,
                expiresIn = tokens.expiresIn,
            )
        } catch (e: Exception) {
            recordFailed("VERIFICATION_FAILED", attemptedEmail = email, reason = e.javaClass.simpleName)
            throw e
        }

        auditWriter.record(
            AuditEvent(
                actorId = response.userId,
                actorType = CUSTOMER,
                action = "VERIFICATION_SUCCEEDED",
                resourceType = "User",
                resourceId = response.userId.toString(),
            )
        )

        log.info("verification complete for user id ${response.userId}")

        return response
    }

    fun login(loginRequest: LoginRequest): String {
        val email = loginRequest.email!!
        val user = userService.findUserByEmail(email)

        return if (user != null) continueForExistingUser(email, user) else fabricatedChallengeSession()
    }

    private fun continueForExistingUser(email: String, user: UserEntity): String =
        if (user.registrationConfirmed) startLoginChallenge(email, user)
        else resendConfirmationCodeForUnconfirmedUser(email, user)

    private fun startLoginChallenge(email: String, user: UserEntity): String {
        val challenge = cognitoClient.startLoginChallenge(CognitoStartLoginChallengeRequest(email))

        auditWriter.record(
            AuditEvent(
                actorId = user.id,
                actorType = CUSTOMER,
                action = "LOGIN_CHALLENGE_STARTED",
                resourceType = "User",
                resourceId = user.id.toString(),
            )
        )

        log.info("login challenge started for user id ${user.id}")
        return challenge.session
    }

    private fun resendConfirmationCodeForUnconfirmedUser(email: String, user: UserEntity): String {
        cognitoClient.resendConfirmationCode(CognitoResendConfirmationCodeRequest(email))

        auditWriter.record(
            AuditEvent(
                actorId = user.id,
                actorType = CUSTOMER,
                action = "CODE_RESENT",
                resourceType = "User",
                resourceId = user.id.toString(),
            )
        )

        log.info("confirmation code re-sent for unconfirmed user id ${user.id}")
        return fabricatedChallengeSession()
    }

    private fun fabricatedChallengeSession(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")

    fun refreshToken(userId: UUID, refreshToken: String): RefreshTokenResponse {
        val response = cognitoClient.refreshToken(
            CognitoRefreshTokenRequest(userId, refreshToken)
        )

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = CUSTOMER,
                action = "REFRESH_ROTATED",
                resourceType = "User",
                resourceId = userId.toString(),
            )
        )

        log.info("refreshed tokens for user id $userId")

        return RefreshTokenResponse(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            idToken = response.idToken,
            expiresIn = response.expiresIn
        )
    }

    fun signOut(accessToken: String, userId: UUID) {
        cognitoClient.signOut(
            CognitoSignOutRequest(accessToken)
        )

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = CUSTOMER,
                action = "LOGOUT",
                resourceType = "User",
                resourceId = userId.toString(),
            )
        )
    }

    private fun recordFailed(action: String, attemptedEmail: String?, reason: String) {
        // Failure-path audit. recordIndependently so the row survives any caller
        // rollback. Secrets are NEVER captured; only the attempted email + the
        // exception class name go into afterState (the exception class is enough
        // to triage at the SOC; the message may carry PII / details).
        auditWriter.recordIndependently(
            AuditEvent(
                actorId = null,
                actorType = SYSTEM,
                action = action,
                afterState = buildMap {
                    if (attemptedEmail != null) put("attemptedEmail", attemptedEmail)
                    put("reason", reason)
                },
            )
        )
    }
}
