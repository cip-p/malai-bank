package com.malai.auth.service

import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.auth.model.UpdateUserRequest
import com.malai.auth.model.UserResponse
import com.malai.auth.repository.UserRepository
import com.malai.commons.TimeService
import com.malai.commons.exception.MalaiCode.MISMATCH_USERS
import com.malai.commons.exception.MalaiCode.USER_NOT_FOUND
import com.malai.commons.exception.UnauthorizedException
import com.malai.commons.exception.UserNotFoundException
import com.malai.security.MalaiRole
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.security.cognito.CognitoClient
import com.malai.security.cognito.model.CognitoGetUserRequest
import com.malai.security.cognito.model.CognitoUpdateUserRequest
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.*

@Service
class UserService(
    private val repository: UserRepository,
    private val timeService: TimeService,
    private val cognitoClient: CognitoClient,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun createUser(
        userId: UUID,
        email: String,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate,
        kycStatus: KycStatus = KycStatus.VERIFIED
    ): UserEntity {
        return repository.save(
            UserEntity(
                id = userId,
                email = email,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                kycStatus = kycStatus,
                registrationConfirmed = false,
                createdAt = timeService.now(),
                createdBy = userId.toString(),
                modifiedAt = null,
                modifiedBy = null
            )
        )
    }

    fun getUser(accessToken: String, userId: UUID, roles: Set<MalaiRole>): UserResponse {
        val cognitoUser = cognitoClient.getUser(CognitoGetUserRequest(accessToken))

        if (userId != cognitoUser.userId) {
            log.error(
                "userIds don't match when get user; request userId {}, cognito userId {}",
                userId,
                cognitoUser.userId
            )
            throw UnauthorizedException(MISMATCH_USERS)
        }

        val user = findUser(userId)

        return UserResponse(user.firstName, user.lastName, user.email, user.dateOfBirth, roles.toRoleNames())
    }

    fun updateUser(accessToken: String, updateUser: UpdateUserRequest, userId: UUID, roles: Set<MalaiRole>): UserResponse {
        val user = getUser(accessToken, userId, roles)
        val before = findUser(userId)

        cognitoClient.updateUser(CognitoUpdateUserRequest(accessToken, "${updateUser.firstName} ${updateUser.lastName}"))

        val databaseUser = before.copy(
            firstName = updateUser.firstName,
            lastName = updateUser.lastName,
            modifiedAt = timeService.now(),
            modifiedBy = userId.toString()
        )

        repository.save(databaseUser)

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "USER_PROFILE_UPDATED",
                resourceType = "User",
                resourceId = userId.toString(),
                beforeState = mapOf("firstName" to before.firstName, "lastName" to before.lastName),
                afterState = mapOf("firstName" to updateUser.firstName, "lastName" to updateUser.lastName),
            )
        )

        return UserResponse(updateUser.firstName, updateUser.lastName, user.email, databaseUser.dateOfBirth, roles.toRoleNames())
    }

    private fun Set<MalaiRole>.toRoleNames(): List<String> = sorted().map { it.name }

    fun findUser(userId: UUID): UserEntity {
        val user = repository.findById(userId)
        if (user.isEmpty) {
            log.error("user not found, userId {}", userId)
            throw UserNotFoundException(USER_NOT_FOUND)
        }
        return user.get()
    }

    fun updateRegistrationConfirmed(email: String): UserEntity {
        val user = findUserByEmail(email)!!
        return repository.save(user.copy(registrationConfirmed = true))
    }

    fun findUserByEmail(email: String): UserEntity? {
        val user = repository.findByEmail(email)
        return if (user.isEmpty) null else user.get()
    }
}
