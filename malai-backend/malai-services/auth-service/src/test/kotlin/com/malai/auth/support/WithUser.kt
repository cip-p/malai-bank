package com.malai.auth.support

import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.auth.repository.UserRepository
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.commons.support.MalaiApiTestContext
import dev.kensa.StateCollector
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Instant
import java.time.LocalDate
import java.util.*

@ExtendWith(WithUser.UserExtension::class)
interface WithUser : WithApplicationContext {

    fun userRepository(): UserRepository =
        MalaiApiTestContext.context(UserContext::class.java).userRepository
            ?: getBean(UserRepository::class.java)

    fun findUserById(id: UUID): Optional<UserEntity> = userRepository().findById(id)

    fun aUserExists(id: UUID, email: String, firstName: String, lastName: String) = CallableAction {
        userRepository().save(
            UserEntity(
                id = id,
                email = email,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = LocalDate.parse("1990-04-12"),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.now(),
                createdBy = "test",
                modifiedAt = Instant.now(),
                modifiedBy = "test"
            )
        )
    }

    fun aUserExistsNotConfirmed(
        id: UUID,
        email: String,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate,
        createdAt: Instant,
        createdBy: String
    ) = CallableAction {
        userRepository().save(
            UserEntity(
                id = id,
                email = email,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = false,
                createdAt = createdAt,
                createdBy = createdBy
            )
        )
    }

    fun aUserExistsConfirmed(
        id: UUID,
        email: String,
        firstName: String,
        lastName: String,
        dateOfBirth: LocalDate,
        createdAt: Instant,
        createdBy: String
    ) = CallableAction {
        userRepository().save(
            UserEntity(
                id = id,
                email = email,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = dateOfBirth,
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = createdAt,
                createdBy = createdBy
            )
        )
    }

    fun theUserInTheDatabase(userId: UUID) = StateCollector<UserEntity> { _ ->
        userRepository().findById(userId).orElseThrow {
            AssertionError("User with ID $userId not found in database")
        }
    }

    class UserExtension : BeforeAllCallback, BeforeEachCallback {
        private var userRepository: UserRepository? = null

        override fun beforeAll(extensionContext: ExtensionContext) {
            if (userRepository == null) {
                userRepository = runCatching {
                    SpringExtension.getApplicationContext(extensionContext)
                        .getBean(UserRepository::class.java)
                }.getOrNull()
            }
            MalaiApiTestContext.context(UserContext::class.java).userRepository = userRepository
        }

        override fun beforeEach(extensionContext: ExtensionContext) {
            userRepository?.deleteAll()
        }
    }

    class UserContext {
        var userRepository: UserRepository? = null
    }
}
