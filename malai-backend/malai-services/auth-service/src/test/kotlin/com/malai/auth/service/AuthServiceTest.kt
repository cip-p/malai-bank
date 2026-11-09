package com.malai.auth.service


import com.malai.auth.model.LoginRequest
import com.malai.auth.model.RegisterRequest
import com.malai.auth.support.CognitoTestData.testAccessToken
import com.malai.auth.support.CognitoTestData.testCognitoRefreshTokenResponse
import com.malai.auth.support.CognitoTestData.testEmail
import com.malai.auth.support.CognitoTestData.testIdToken
import com.malai.auth.support.CognitoTestData.testRefreshToken
import com.malai.auth.support.CognitoTestData.testUserId
import com.malai.auth.support.SpringTest
import com.malai.auth.support.WithUser
import com.malai.security.audit.ActorType
import com.malai.security.audit.ActorType.CUSTOMER
import com.malai.security.audit.ActorType.SYSTEM
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.security.cognito.CognitoClient
import com.malai.security.cognito.model.CognitoLoginChallenge
import com.malai.test.commons.MockitoHelper.anyObject
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.time.Instant
import java.time.LocalDate

@SpringTest
class AuthServiceTest : WithUser {

    @Autowired
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var cognitoClient: CognitoClient

    @MockitoBean
    private lateinit var auditWriter: AuditWriter

    @MockitoBean
    private lateinit var registrationOrchestrator: RegistrationOrchestrator

    @Nested
    inner class Login {

        @Test
        fun `starts a login challenge and returns its session for a confirmed user`() {
            `when`(cognitoClient.startLoginChallenge(anyObject())).thenReturn(CognitoLoginChallenge("some-challenge-session"))
            aUserExists(testUserId, testEmail, "Alice", "Smith").execute()

            val challengeSession = authService.login(LoginRequest(testEmail))

            assertThat(challengeSession).isEqualTo("some-challenge-session")
        }

        @Test
        fun `emits LOGIN_CHALLENGE_STARTED audit event for a confirmed user`() {
            `when`(cognitoClient.startLoginChallenge(anyObject())).thenReturn(CognitoLoginChallenge("some-challenge-session"))
            aUserExists(testUserId, testEmail, "Alice", "Smith").execute()
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            authService.login(LoginRequest(testEmail))

            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("LOGIN_CHALLENGE_STARTED")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
            assertThat(event.resourceType).isEqualTo("User")
            assertThat(event.resourceId).isEqualTo(testUserId.toString())
        }

        @Test
        fun `returns a fabricated session and starts no challenge for an unknown email`() {
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            val challengeSession = authService.login(LoginRequest(testEmail))

            assertThat(challengeSession).isNotBlank()
            assertThat(captured).isEmpty()
            verify(cognitoClient, never()).startLoginChallenge(anyObject())
        }

        @Test
        fun `re-sends the confirmation code and starts no challenge for an unconfirmed user`() {
            doNothing().`when`(cognitoClient).resendConfirmationCode(anyObject())
            aUserExistsNotConfirmed(
                testUserId, testEmail, "Alice", "Smith",
                LocalDate.parse("1990-04-12"), Instant.parse("2026-10-15T10:30:00Z"), "test"
            ).execute()
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            val challengeSession = authService.login(LoginRequest(testEmail))

            assertThat(challengeSession).isNotBlank()
            verify(cognitoClient, never()).startLoginChallenge(anyObject())
            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("CODE_RESENT")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
        }
    }

    @Nested
    inner class RefreshToken {

        @Test
        fun `returns refreshed Cognito tokens on refresh`() {
            `when`(cognitoClient.refreshToken(anyObject())).thenReturn(testCognitoRefreshTokenResponse)

            val refreshTokenResponse = authService.refreshToken(testUserId, "some-refresh-token")

            assertThat(refreshTokenResponse.accessToken).isEqualTo(testAccessToken)
            assertThat(refreshTokenResponse.refreshToken).isEqualTo(testRefreshToken)
            assertThat(refreshTokenResponse.idToken).isEqualTo(testIdToken)
        }

        @Test
        fun `emits REFRESH_ROTATED audit event on token rotation`() {
            `when`(cognitoClient.refreshToken(anyObject())).thenReturn(testCognitoRefreshTokenResponse)
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            authService.refreshToken(testUserId, "some-refresh-token")

            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("REFRESH_ROTATED")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
        }
    }

    @Nested
    inner class Register {

        @Test
        fun `emits REGISTERED audit event on successful registration`() {
            `when`(registrationOrchestrator.register(anyObject())).thenReturn(testUserId)
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            authService.register(aRegisterRequest())

            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("REGISTERED")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
            assertThat(event.resourceType).isEqualTo("User")
            assertThat(event.resourceId).isEqualTo(testUserId.toString())
        }

        @Test
        fun `emits REGISTER_FAILED audit event when the orchestrator throws`() {
            `when`(registrationOrchestrator.register(anyObject()))
                .thenThrow(RuntimeException("downstream-error"))
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            assertThatThrownBy { authService.register(aRegisterRequest()) }
                .isInstanceOf(RuntimeException::class.java)

            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("REGISTER_FAILED")
            assertThat(event.actorType).isEqualTo(SYSTEM)
            assertThat(event.actorId).isNull()
            assertThat(event.afterState).isEqualTo(mapOf("attemptedEmail" to testEmail, "reason" to "RuntimeException"))
        }

        @Test
        fun `degrades to a login challenge for a taken confirmed email, discarding the name and not re-registering`() {
            `when`(cognitoClient.startLoginChallenge(anyObject())).thenReturn(CognitoLoginChallenge("some-challenge-session"))
            aUserExists(testUserId, testEmail, "Alice", "Smith").execute()
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            val challengeSession = authService.register(aRegisterRequest())

            assertThat(challengeSession).isEqualTo("some-challenge-session")
            verify(registrationOrchestrator, never()).register(anyObject())
            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("LOGIN_CHALLENGE_STARTED")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
        }

        @Test
        fun `re-sends the confirmation code for an unconfirmed email and does not re-register`() {
            doNothing().`when`(cognitoClient).resendConfirmationCode(anyObject())
            aUserExistsNotConfirmed(
                testUserId, testEmail, "Alice", "Smith",
                LocalDate.parse("1990-04-12"), Instant.parse("2026-10-15T10:30:00Z"), "test"
            ).execute()
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            val challengeSession = authService.register(aRegisterRequest())

            assertThat(challengeSession).isNotBlank()
            verify(registrationOrchestrator, never()).register(anyObject())
            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("CODE_RESENT")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
        }
    }

    private fun aRegisterRequest() = RegisterRequest(
        firstName = "Alice",
        lastName = "Smith",
        email = testEmail,
    )

    @Nested
    inner class SignOut {

        @Test
        fun `sign out using the access token`() {
            doNothing().`when`(cognitoClient).signOut(anyObject())

            authService.signOut(testAccessToken, testUserId)
        }

        @Test
        fun `emits LOGOUT audit event on signOut`() {
            doNothing().`when`(cognitoClient).signOut(anyObject())
            val captured = mutableListOf<AuditEvent>()
            captureAuditEventInto(captured, auditWriter)

            authService.signOut(testAccessToken, testUserId)

            assertThat(captured).hasSize(1)
            val event = captured.last()
            assertThat(event.action).isEqualTo("LOGOUT")
            assertThat(event.actorType).isEqualTo(CUSTOMER)
            assertThat(event.actorId).isEqualTo(testUserId)
        }
    }
}

// Side-effect capture for Mockito + Kotlin: ArgumentCaptor.capture() returns null
// at runtime which Kotlin rejects with "capture(...) must not be null" because the
// stubbed method's parameter is non-nullable. doAnswer bypasses this -- the answer
// stores the AuditEvent argument into [out] and returns Unit, so verify(mock) can
// then assert on [out.last()]. Both record() and recordIndependently() route to the
// same capture list so success-path and failure-path audits are observable
// uniformly. Each test in this class wires its own capture list.
private fun captureAuditEventInto(out: MutableList<AuditEvent>, mock: AuditWriter) {
    val capture = org.mockito.stubbing.Answer<Any?> { invocation ->
        out.add(invocation.arguments[0] as AuditEvent)
        null
    }
    doAnswer(capture).`when`(mock).record(com.malai.test.commons.MockitoHelper.anyObject())
    doAnswer(capture).`when`(mock).recordIndependently(com.malai.test.commons.MockitoHelper.anyObject())
}
