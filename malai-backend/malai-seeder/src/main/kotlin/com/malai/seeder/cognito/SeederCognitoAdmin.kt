package com.malai.seeder.cognito

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminCreateUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDeleteUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminGetUserResponse
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminSetUserPasswordRequest
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType
import software.amazon.awssdk.services.cognitoidentityprovider.model.MessageActionType
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException
import software.amazon.awssdk.services.cognitoidentityprovider.model.UsernameExistsException
import java.util.UUID

/**
 * Thin wrapper over Cognito Admin API operations the seeder needs.
 *
 * When `MAL_AWS_COGNITO_USER_POOL_ID` is blank (local-dev mode without AWS
 * creds), every call short-circuits and logs a `SKIP` line. The local
 * docker-compose Postgres still gets seeded; only the Cognito side is omitted.
 *
 * Plan 02-15 footnote: prod Cognito hardening (password rotation, MFA, account
 * lockout policy) lands in Phase 5; the demo password lives in
 * [com.malai.seeder.data.SarahMitchellSeed.DEMO_PASSWORD] and is documented as
 * dev-only.
 */
@Component
class SeederCognitoAdmin(
    // Disambiguate from `malai-security`'s `identityProviderClient` bean, which is built
    // with `AnonymousCredentialsProvider` for the SECRET_HASH-authed user-facing flows
    // and would 401 on every Admin* call here. Our `seederCognitoIdentityProviderClient`
    // uses the default credentials chain (the ECS task role on Fargate; ambient creds locally).
    @Qualifier("seederCognitoIdentityProviderClient")
    private val client: CognitoIdentityProviderClient,
    @Value("\${malai.aws.cognito.user-pool-id:}") private val userPoolId: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun isEnabled(): Boolean = userPoolId.isNotBlank()

    // Returns the Cognito `sub` for the created (or pre-existing) user, or null when
    // Cognito is disabled. The seeder uses this to key the auth-service `users` row on
    // the same UUID Cognito will hand the webapp at login — without it, every login
    // against a real user pool 404s with MalaiCode 8979 (USER_NOT_FOUND).
    fun adminCreateUser(email: String, fullName: String): UUID? {
        if (!isEnabled()) {
            log.info("SKIP Cognito AdminCreateUser email={} — MAL_AWS_COGNITO_USER_POOL_ID is blank", email)
            return null
        }
        val sub = try {
            val response: AdminCreateUserResponse = client.adminCreateUser(
                AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .userAttributes(
                        AttributeType.builder().name("email").value(email).build(),
                        AttributeType.builder().name("email_verified").value("true").build(),
                        AttributeType.builder().name("name").value(fullName).build(),
                    )
                    .messageAction(MessageActionType.SUPPRESS)
                    .build()
            )
            log.info("Cognito AdminCreateUser ok email={}", email)
            response.user().attributes().first { it.name() == "sub" }.value()
        } catch (_: UsernameExistsException) {
            // Idempotent re-seed: user already exists in the pool. Fetch its sub so we
            // still bind the DB row to the same Cognito identity instead of regenerating.
            log.info("Cognito AdminCreateUser already-exists email={} — fetching existing sub", email)
            val getResponse: AdminGetUserResponse = client.adminGetUser(
                AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build()
            )
            getResponse.userAttributes().first { it.name() == "sub" }.value()
        }
        return UUID.fromString(sub)
    }

    fun adminSetUserPassword(email: String, password: String, permanent: Boolean) {
        if (!isEnabled()) {
            log.info("SKIP Cognito AdminSetUserPassword email={}", email)
            return
        }
        client.adminSetUserPassword(
            AdminSetUserPasswordRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .password(password)
                .permanent(permanent)
                .build()
        )
        log.info("Cognito AdminSetUserPassword ok email={} permanent={}", email, permanent)
    }

    fun adminAddUserToGroup(email: String, groupName: String) {
        if (!isEnabled()) {
            log.info("SKIP Cognito AdminAddUserToGroup email={} group={}", email, groupName)
            return
        }
        client.adminAddUserToGroup(
            AdminAddUserToGroupRequest.builder()
                .userPoolId(userPoolId)
                .username(email)
                .groupName(groupName)
                .build()
        )
        log.info("Cognito AdminAddUserToGroup ok email={} group={}", email, groupName)
    }

    fun adminDeleteUser(email: String) {
        if (!isEnabled()) {
            log.info("SKIP Cognito AdminDeleteUser email={}", email)
            return
        }
        try {
            client.adminDeleteUser(
                AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build()
            )
            log.info("Cognito AdminDeleteUser ok email={}", email)
        } catch (_: UserNotFoundException) {
            log.info("Cognito AdminDeleteUser not-found email={} — idempotent", email)
        }
    }
}
