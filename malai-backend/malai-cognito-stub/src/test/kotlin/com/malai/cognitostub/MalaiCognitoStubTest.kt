package com.malai.cognitostub

import tools.jackson.module.kotlin.jacksonObjectMapper
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType
import software.amazon.awssdk.services.cognitoidentityprovider.model.ChallengeNameType
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotConfirmedException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.util.Base64

class MalaiCognitoStubTest {

    @TempDir
    lateinit var stateDir: Path

    private lateinit var server: MalaiCognitoStubServer
    private lateinit var cognito: CognitoIdentityProviderClient
    private val http = HttpClient.newHttpClient()

    @BeforeEach
    fun startStub() {
        server = MalaiCognitoStubServer(StubSettings(port = 0, stateDir = stateDir, issuer = null)).start()
        cognito = cognitoClient()
    }

    @AfterEach
    fun stopStub() {
        cognito.close()
        server.stop()
    }

    @Test
    fun registersConfirmsAndLogsInAsTheSameSubject() {
        val userSub = cognito.signUp {
            it.clientId(CLIENT_ID).username(EMAIL).userAttributes(attribute("email", EMAIL), attribute("name", "Ada Lovelace"))
        }.userSub()
        val session = cognito.confirmSignUp { it.clientId(CLIENT_ID).username(EMAIL).confirmationCode("000000") }.session()
        val verification = cognito.initiateAuth {
            it.clientId(CLIENT_ID).authFlow(AuthFlowType.USER_AUTH).session(session).authParameters(mapOf("USERNAME" to EMAIL))
        }.authenticationResult()
        cognito.adminAddUserToGroup { it.userPoolId(USER_POOL_ID).username(EMAIL).groupName("customer") }

        val challenge = cognito.initiateAuth {
            it.clientId(CLIENT_ID).authFlow(AuthFlowType.USER_AUTH)
                .authParameters(mapOf("USERNAME" to EMAIL, "PREFERRED_CHALLENGE" to "EMAIL_OTP"))
        }
        val login = cognito.respondToAuthChallenge {
            it.clientId(CLIENT_ID).challengeName(ChallengeNameType.EMAIL_OTP).session(challenge.session())
                .challengeResponses(mapOf("USERNAME" to EMAIL, "EMAIL_OTP_CODE" to "123456"))
        }.authenticationResult()

        assertThat(challenge.challengeName()).isEqualTo(ChallengeNameType.EMAIL_OTP)
        assertThat(verifiedClaims(verification.accessToken()).subject).isEqualTo(userSub)
        val claims = verifiedClaims(login.accessToken())
        assertThat(claims.subject).isEqualTo(userSub)
        assertThat(claims.issuer).isEqualTo(server.issuer)
        assertThat(claims.getStringClaim("token_use")).isEqualTo("access")
        assertThat(claims.getStringListClaim("cognito:groups")).containsExactly("customer")
        assertThat(verifiedClaims(login.idToken()).getStringClaim("token_use")).isEqualTo("id")
        assertThat(login.expiresIn()).isEqualTo(3600)
        assertThat(login.refreshToken()).isNotBlank()
    }

    @Test
    fun refreshesTheAccessTokenWithoutRotatingTheRefreshToken() {
        val userSub = registerAndConfirm()
        val login = logIn()

        val refreshed = cognito.initiateAuth {
            it.clientId(CLIENT_ID).authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                .authParameters(mapOf("REFRESH_TOKEN" to login.refreshToken()))
        }.authenticationResult()

        assertThat(verifiedClaims(refreshed.accessToken()).subject).isEqualTo(userSub)
        assertThat(refreshed.refreshToken()).isNull()
    }

    @Test
    fun rejectsALoginChallengeForAnUnknownUser() {
        assertThatThrownBy { startLoginChallenge() }.isInstanceOf(UserNotFoundException::class.java)
    }

    @Test
    fun rejectsALoginChallengeBeforeTheSignUpIsConfirmed() {
        cognito.signUp { it.clientId(CLIENT_ID).username(EMAIL).userAttributes(attribute("email", EMAIL)) }

        assertThatThrownBy { startLoginChallenge() }.isInstanceOf(UserNotConfirmedException::class.java)
    }

    @Test
    fun readsTheUserBehindAnAccessToken() {
        val userSub = registerAndConfirm()
        val login = logIn()

        val user = cognito.getUser { it.accessToken(login.accessToken()) }

        assertThat(user.userAttributes().associate { it.name() to it.value() })
            .containsEntry("sub", userSub)
            .containsEntry("email", EMAIL)
            .containsEntry("name", "Ada Lovelace")
    }

    @Test
    fun adminCreatedUsersLogInWithTheSubjectTheSeederWasGiven() {
        val created = cognito.adminCreateUser {
            it.userPoolId(USER_POOL_ID).username(EMAIL).messageAction(MessageActionType.SUPPRESS)
                .userAttributes(attribute("email", EMAIL), attribute("name", "Sarah Mitchell"))
        }
        cognito.adminSetUserPassword { it.userPoolId(USER_POOL_ID).username(EMAIL).password("Password123!").permanent(true) }
        val fetched = cognito.adminGetUser { it.userPoolId(USER_POOL_ID).username(EMAIL) }

        val createdSub = created.user().attributes().first { it.name() == "sub" }.value()
        assertThat(fetched.userAttributes().first { it.name() == "sub" }.value()).isEqualTo(createdSub)
        assertThat(verifiedClaims(logIn().accessToken()).subject).isEqualTo(createdSub)
    }

    @Test
    fun issuesServiceTokensForClientCredentials() {
        val basic = Base64.getEncoder().encodeToString("service-client:service-secret".toByteArray())
        val request = HttpRequest.newBuilder(URI.create("${server.baseUrl()}/oauth2/token"))
            .header("Authorization", "Basic $basic")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials&scope=malai-internal%2Fonboarding.write"))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        assertThat(response.statusCode()).isEqualTo(200)
        val body = jacksonObjectMapper().readTree(response.body())
        val claims = verifiedClaims(body["access_token"].asString())
        assertThat(claims.getStringClaim("client_id")).isEqualTo("service-client")
        assertThat(claims.getStringClaim("scope")).isEqualTo("malai-internal/onboarding.write")
        assertThat(claims.getStringClaim("token_use")).isEqualTo("access")
        assertThat(body["expires_in"].asLong()).isEqualTo(3600)
    }

    @Test
    fun keepsUsersAndSigningKeyAcrossRestarts() {
        val userSub = registerAndConfirm()
        val tokenIssuedBeforeRestart = logIn().accessToken()
        val port = server.port
        server.stop()
        cognito.close()

        server = MalaiCognitoStubServer(StubSettings(port = port, stateDir = stateDir, issuer = null)).start()
        cognito = cognitoClient()

        assertThat(verifiedClaims(tokenIssuedBeforeRestart).subject).isEqualTo(userSub)
        assertThat(cognito.getUser { it.accessToken(tokenIssuedBeforeRestart) }.username()).isEqualTo(EMAIL)
        assertThat(verifiedClaims(logIn().accessToken()).subject).isEqualTo(userSub)
    }

    @Test
    fun resetForgetsEveryUser() {
        registerAndConfirm()

        val reset = http.send(
            HttpRequest.newBuilder(URI.create("${server.baseUrl()}/stub/reset")).POST(HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofString(),
        )

        assertThat(reset.statusCode()).isEqualTo(200)
        assertThatThrownBy { startLoginChallenge() }.isInstanceOf(UserNotFoundException::class.java)
    }

    private fun registerAndConfirm(): String {
        val userSub = cognito.signUp {
            it.clientId(CLIENT_ID).username(EMAIL).userAttributes(attribute("email", EMAIL), attribute("name", "Ada Lovelace"))
        }.userSub()
        cognito.confirmSignUp { it.clientId(CLIENT_ID).username(EMAIL).confirmationCode("000000") }
        return userSub
    }

    private fun startLoginChallenge() = cognito.initiateAuth {
        it.clientId(CLIENT_ID).authFlow(AuthFlowType.USER_AUTH)
            .authParameters(mapOf("USERNAME" to EMAIL, "PREFERRED_CHALLENGE" to "EMAIL_OTP"))
    }

    private fun logIn(): AuthenticationResultType {
        val challenge = startLoginChallenge()
        return cognito.respondToAuthChallenge {
            it.clientId(CLIENT_ID).challengeName(ChallengeNameType.EMAIL_OTP).session(challenge.session())
                .challengeResponses(mapOf("USERNAME" to EMAIL, "EMAIL_OTP_CODE" to "123456"))
        }.authenticationResult()
    }

    private fun verifiedClaims(token: String): JWTClaimsSet {
        val jwksResponse = http.send(
            HttpRequest.newBuilder(URI.create("${server.issuer}/.well-known/jwks.json")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        val jwt = SignedJWT.parse(token)
        val key = JWKSet.parse(jwksResponse.body()).getKeyByKeyId(jwt.header.keyID).toRSAKey()
        assertThat(jwt.verify(RSASSAVerifier(key))).isTrue()
        return jwt.jwtClaimsSet
    }

    private fun cognitoClient(): CognitoIdentityProviderClient = CognitoIdentityProviderClient.builder()
        .region(Region.US_EAST_1)
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
        .endpointOverride(URI.create(server.baseUrl()))
        .build()

    private fun attribute(name: String, value: String): AttributeType = AttributeType.builder().name(name).value(value).build()

    companion object {
        private const val CLIENT_ID = "local-client-id"
        private const val USER_POOL_ID = "local-user-pool"
        private const val EMAIL = "ada@example.com"
    }
}
