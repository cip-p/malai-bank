package com.malai.cognitostub

import tools.jackson.databind.JsonNode
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformerV2
import com.github.tomakehurst.wiremock.http.ResponseDefinition
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import org.slf4j.LoggerFactory
import java.util.UUID

class CognitoIdpTransformer(
    private val users: UserDirectory,
    private val tokens: TokenMinter,
) : ResponseDefinitionTransformerV2 {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun getName() = NAME

    override fun applyGlobally() = false

    override fun transform(serveEvent: ServeEvent): ResponseDefinition {
        val operation = AwsJson.header(serveEvent.request, AwsJson.TARGET_HEADER).orEmpty().substringAfter('.')
        val request = AwsJson.parse(serveEvent.request.bodyAsString)
        return try {
            AwsJson.ok(serveEvent, handle(operation, request))
        } catch (error: CognitoError) {
            log.warn("{} rejected: {} ({})", operation, error.type, error.message)
            AwsJson.error(serveEvent, error)
        }
    }

    private fun handle(operation: String, request: JsonNode): Map<String, Any> = when (operation) {
        "SignUp" -> signUp(request)
        "ConfirmSignUp" -> confirmSignUp(request)
        "ResendConfirmationCode" -> resendConfirmationCode(request)
        "InitiateAuth" -> initiateAuth(request)
        "RespondToAuthChallenge" -> respondToAuthChallenge(request)
        "GlobalSignOut" -> emptyMap()
        "GetUser" -> getUser(request)
        "UpdateUserAttributes" -> updateUserAttributes(request)
        "AdminCreateUser" -> adminCreateUser(request)
        "AdminGetUser" -> adminGetUser(request)
        "AdminSetUserPassword" -> adminSetUserPassword(request)
        "AdminAddUserToGroup" -> adminChangeGroups(request) { it + request.groupName() }
        "AdminRemoveUserFromGroup" -> adminChangeGroups(request) { it - request.groupName() }
        "AdminListGroupsForUser" -> adminListGroupsForUser(request)
        "AdminDeleteUser" -> adminDeleteUser(request)
        else -> throw CognitoError("UnknownOperationException", "$operation is not supported by the malai Cognito stub")
    }

    private fun signUp(request: JsonNode): Map<String, Any> {
        val email = request.username()
        val attributes = request.userAttributes()
        val existing = users.find(email)
        val user = users.save(
            StubUser(
                sub = existing?.sub ?: UUID.randomUUID(),
                email = email,
                name = attributes["name"] ?: existing?.name ?: "",
                confirmed = false,
                groups = existing?.groups ?: emptySet(),
            )
        )
        log.info("signed up {} as {}{}", user.email, user.sub, if (existing != null) " (re-registered)" else "")
        announceCode("sign-up confirmation", user, "123456")
        return mapOf(
            "UserSub" to user.sub.toString(),
            "UserConfirmed" to false,
            "CodeDeliveryDetails" to codeDeliveryDetails(user),
        )
    }

    private fun confirmSignUp(request: JsonNode): Map<String, Any> {
        val user = users.save(requireUser(request.username()).copy(confirmed = true))
        log.info("confirmed sign-up for {} with code {}", user.email, request.text("ConfirmationCode"))
        return mapOf("Session" to newSession())
    }

    private fun resendConfirmationCode(request: JsonNode): Map<String, Any> {
        val user = requireUser(request.username())
        announceCode("sign-up confirmation", user, "123456")
        return mapOf("CodeDeliveryDetails" to codeDeliveryDetails(user))
    }

    private fun initiateAuth(request: JsonNode): Map<String, Any> {
        val parameters = request.stringMap("AuthParameters")
        return when (val flow = request.text("AuthFlow")) {
            "REFRESH_TOKEN_AUTH", "REFRESH_TOKEN" -> refresh(parameters["REFRESH_TOKEN"], request.clientId())
            "USER_AUTH" -> userAuth(parameters, request.has("Session"), request.clientId())
            "USER_PASSWORD_AUTH", "USER_SRP_AUTH", "ADMIN_USER_PASSWORD_AUTH" ->
                authenticated(requireConfirmedUser(parameters.username()), request.clientId())
            else -> throw CognitoError.invalidParameter("unsupported AuthFlow $flow")
        }
    }

    private fun userAuth(parameters: Map<String, String>, exchangingSession: Boolean, clientId: String): Map<String, Any> {
        val user = requireConfirmedUser(parameters.username())
        if (exchangingSession) {
            return authenticated(user, clientId)
        }
        announceCode("login", user, "12345678")
        return mapOf(
            "ChallengeName" to "EMAIL_OTP",
            "Session" to newSession(),
            "ChallengeParameters" to mapOf(
                "CODE_DELIVERY_DELIVERY_MEDIUM" to "EMAIL",
                "CODE_DELIVERY_DESTINATION" to masked(user.email),
            ),
        )
    }

    private fun respondToAuthChallenge(request: JsonNode): Map<String, Any> {
        val responses = request.stringMap("ChallengeResponses")
        val user = requireConfirmedUser(responses.username())
        log.info("answered {} challenge for {} with code {}", request.text("ChallengeName"), user.email, responses["EMAIL_OTP_CODE"])
        return authenticated(user, request.clientId())
    }

    private fun refresh(refreshToken: String?, clientId: String): Map<String, Any> {
        val sub = refreshToken?.let(tokens::subjectOfRefreshToken)
            ?: throw CognitoError.notAuthorized("Invalid Refresh Token")
        val user = users.findBySub(sub) ?: throw CognitoError.userNotFound()
        log.info("refreshed tokens for {}", user.email)
        return mapOf(
            "AuthenticationResult" to authenticationResult(user, clientId, includeRefreshToken = false),
            "ChallengeParameters" to emptyMap<String, String>(),
        )
    }

    private fun authenticated(user: StubUser, clientId: String): Map<String, Any> {
        log.info("issued tokens for {} ({}) groups={}", user.email, user.sub, user.groups)
        return mapOf(
            "AuthenticationResult" to authenticationResult(user, clientId, includeRefreshToken = true),
            "ChallengeParameters" to emptyMap<String, String>(),
        )
    }

    private fun authenticationResult(user: StubUser, clientId: String, includeRefreshToken: Boolean): Map<String, Any> = buildMap {
        put("AccessToken", tokens.accessToken(user, clientId))
        put("IdToken", tokens.idToken(user, clientId))
        if (includeRefreshToken) {
            put("RefreshToken", tokens.refreshToken(user))
        }
        put("ExpiresIn", TokenMinter.ACCESS_TOKEN_TTL.seconds)
        put("TokenType", "Bearer")
    }

    private fun getUser(request: JsonNode): Map<String, Any> {
        val user = userFromAccessToken(request)
        return mapOf("Username" to user.email, "UserAttributes" to attributesOf(user))
    }

    private fun updateUserAttributes(request: JsonNode): Map<String, Any> {
        val user = userFromAccessToken(request)
        val attributes = request.userAttributes()
        val updated = user.copy(name = attributes["name"] ?: user.name, email = attributes["email"] ?: user.email)
        if (!updated.email.equals(user.email, ignoreCase = true)) {
            users.remove(user.email)
        }
        users.save(updated)
        return emptyMap()
    }

    private fun adminCreateUser(request: JsonNode): Map<String, Any> {
        val email = request.username()
        val attributes = request.userAttributes()
        val existing = users.find(email)
        val user = users.save(
            StubUser(
                sub = existing?.sub ?: UUID.randomUUID(),
                email = email,
                name = attributes["name"] ?: existing?.name ?: "",
                confirmed = true,
                groups = existing?.groups ?: emptySet(),
            )
        )
        log.info("admin-created {} as {}{}", user.email, user.sub, if (existing != null) " (already existed)" else "")
        return mapOf("User" to userSummary(user, attributesField = "Attributes"))
    }

    private fun adminGetUser(request: JsonNode): Map<String, Any> =
        userSummary(requireUser(request.username()), attributesField = "UserAttributes")

    private fun adminSetUserPassword(request: JsonNode): Map<String, Any> {
        users.save(requireUser(request.username()).copy(confirmed = true))
        return emptyMap()
    }

    private fun adminChangeGroups(request: JsonNode, change: (Set<String>) -> Set<String>): Map<String, Any> {
        val user = requireUser(request.username())
        val updated = users.save(user.copy(groups = change(user.groups)))
        log.info("groups for {} are now {}", updated.email, updated.groups)
        return emptyMap()
    }

    private fun adminListGroupsForUser(request: JsonNode): Map<String, Any> =
        mapOf("Groups" to requireUser(request.username()).groups.map { mapOf("GroupName" to it) })

    private fun adminDeleteUser(request: JsonNode): Map<String, Any> {
        val removed = users.remove(request.username()) ?: throw CognitoError.userNotFound()
        log.info("admin-deleted {}", removed.email)
        return emptyMap()
    }

    private fun userFromAccessToken(request: JsonNode): StubUser {
        val sub = request.text("AccessToken")?.let(tokens::subjectOfAccessToken)
            ?: throw CognitoError.notAuthorized("Invalid Access Token")
        return users.findBySub(sub) ?: throw CognitoError.userNotFound()
    }

    private fun requireUser(email: String): StubUser = users.find(email) ?: throw CognitoError.userNotFound()

    private fun requireConfirmedUser(email: String): StubUser =
        requireUser(email).takeIf { it.confirmed } ?: throw CognitoError.userNotConfirmed()

    private fun userSummary(user: StubUser, attributesField: String): Map<String, Any> = mapOf(
        "Username" to user.email,
        attributesField to attributesOf(user),
        "UserStatus" to if (user.confirmed) "CONFIRMED" else "UNCONFIRMED",
        "Enabled" to true,
    )

    private fun attributesOf(user: StubUser): List<Map<String, String>> = listOf(
        attribute("sub", user.sub.toString()),
        attribute("email", user.email),
        attribute("email_verified", "true"),
        attribute("name", user.name),
    )

    private fun attribute(name: String, value: String) = mapOf("Name" to name, "Value" to value)

    private fun codeDeliveryDetails(user: StubUser) = mapOf(
        "AttributeName" to "email",
        "DeliveryMedium" to "EMAIL",
        "Destination" to masked(user.email),
    )

    private fun masked(email: String): String {
        val domain = email.substringAfter('@', missingDelimiterValue = "")
        return if (domain.isEmpty()) "${email.take(1)}***" else "${email.take(1)}***@$domain"
    }

    private fun announceCode(purpose: String, user: StubUser, example: String) {
        log.info("{} code sent to {}: any code is accepted, e.g. {}", purpose, user.email, example)
    }

    private fun newSession(): String = UUID.randomUUID().toString().replace("-", "")

    companion object {
        const val NAME = "cognito-idp"
    }
}
