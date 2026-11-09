package com.malai.security.cognito

import com.malai.commons.exception.InternalServerErrorException
import com.malai.commons.exception.MalaiCode.COGNITO_ADD_USER_TO_GROUP_ERROR
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminAddUserToGroupRequest

/**
 * IAM-signed Cognito Admin operations needed by application code (not the seeder).
 *
 * Admin* operations bypass SECRET_HASH and are authenticated by the caller's
 * IAM identity (the ECS task role in prod). They run against
 * [adminIdentityProviderClient] (default credentials chain) — separate from the
 * [CognitoClient] that wraps the user-facing SECRET_HASH flows with
 * `AnonymousCredentialsProvider`.
 *
 * When `MAL_AWS_COGNITO_USER_POOL_ID` is blank — local dev without AWS creds,
 * or any non-prod path that doesn't bind a user pool — every call short-circuits
 * with a SKIP log line. This mirrors the seeder's [com.malai.seeder.cognito.SeederCognitoAdmin].
 */
@Service
class CognitoAdminClient(
    @Qualifier("adminIdentityProviderClient")
    private val client: CognitoIdentityProviderClient,
    @Value("\${malai.aws.cognito.user-pool-id:}") private val userPoolId: String,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun isEnabled(): Boolean = userPoolId.isNotBlank()

    fun addUserToGroup(email: String, groupName: String) {
        if (!isEnabled()) {
            log.info("SKIP Cognito AdminAddUserToGroup email={} group={} — MAL_AWS_COGNITO_USER_POOL_ID is blank", email, groupName)
            return
        }
        try {
            client.adminAddUserToGroup(
                AdminAddUserToGroupRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .groupName(groupName)
                    .build()
            )
            log.info("Cognito AdminAddUserToGroup ok email={} group={}", email, groupName)
        } catch (ex: Exception) {
            log.error("Cognito AdminAddUserToGroup error email=$email group=$groupName", ex)
            throw InternalServerErrorException(COGNITO_ADD_USER_TO_GROUP_ERROR)
        }
    }
}
