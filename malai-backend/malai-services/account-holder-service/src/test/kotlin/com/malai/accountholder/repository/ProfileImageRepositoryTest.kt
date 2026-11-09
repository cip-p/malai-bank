package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.ProfileImageEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.util.*

@SpringTest
class ProfileImageRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var profileImageRepository: ProfileImageRepository

    @BeforeEach
    fun setUp() {
        persistAccountHolder(someAccountHolderUid())
        persistAccountHolder(anotherAccountHolderUid())
    }

    @Test
    fun findByAccountHolderIdReturnsProfileImageForAccountHolder() {
        val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        persistProfileImage(someProfileImageUid(), someAccountHolderUid(), "image/png", imageData)

        val result = profileImageRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveComparison().isEqualTo(ProfileImageEntity(
            someProfileImageUid(),
            someAccountHolderUid(),
            "image/png",
            imageData
        ))
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenImageBelongsToDifferentAccountHolder() {
        val imageData = byteArrayOf(0xFF.toByte(), 0xD8.toByte())
        persistProfileImage(someProfileImageUid(), anotherAccountHolderUid(), "image/jpeg", imageData)

        val result = profileImageRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun findByAccountHolderIdReturnsNullWhenNoImageExists() {
        val result = profileImageRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isNull()
    }

    @Test
    fun deleteByAccountHolderIdRemovesImage() {
        val imageData = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        persistProfileImage(someProfileImageUid(), someAccountHolderUid(), "image/png", imageData)

        profileImageRepository.deleteByAccountHolderId(someAccountHolderUid())
        entityManager().flush()
        entityManager().clear()

        val result = profileImageRepository.findByAccountHolderId(someAccountHolderUid())
        assertThat(result).isNull()
    }

    private fun persistAccountHolder(accountHolderId: UUID) {
        persistAndFlush(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = someUserId(),
                accountHolderType = INDIVIDUAL,
                createdAt = Instant.parse("2025-10-15T10:00:00Z"),
                status = PENDING,
            )
        )
    }

    private fun persistProfileImage(profileImageId: UUID, accountHolderId: UUID, contentType: String, imageData: ByteArray) {
        persistAndFlush(
            ProfileImageEntity(
                profileImageId = profileImageId,
                accountHolderId = accountHolderId,
                contentType = contentType,
                imageData = imageData
            )
        )
    }
}
