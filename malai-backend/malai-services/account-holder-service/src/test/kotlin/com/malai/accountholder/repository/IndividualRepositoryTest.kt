package com.malai.accountholder.repository

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.IndividualEntity
import com.malai.accountholder.support.SpringTest
import com.malai.client.malai.MalaiTestFixtures
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.test.commons.support.WithEntityManager
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

@SpringTest
class IndividualRepositoryTest : WithEntityManager, MalaiTestFixtures {

    @Autowired
    private lateinit var individualRepository: IndividualRepository

    @BeforeEach
    fun setUp() {
        persistAccountHolder(someAccountHolderUid())
        persistAccountHolder(anotherAccountHolderUid())
    }

    @Test
    fun findByAccountHolderIdReturnsAllIndividualsForAccountHolder() {
        persistIndividual(
            someIndividualUid(),
            someAccountHolderUid(),
            "Mr",
            "John",
            "Doe",
            "1990-01-15",
            "john@example.com",
            "+441234567890",
            PENDING
        )
        persistIndividual(
            anotherIndividualUid(),
            someAccountHolderUid(),
            "Mrs",
            "Jane",
            "Doe",
            "1992-03-20",
            "jane@example.com",
            "+441234567891",
            ACTIVE
        )

        val result = individualRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).usingRecursiveFieldByFieldElementComparator().containsExactlyInAnyOrder(
            IndividualEntity(
                individualId = someIndividualUid(),
                accountHolderId = someAccountHolderUid(),
                title = "Mr",
                firstName = "John",
                lastName = "Doe",
                dateOfBirth = LocalDate.parse("1990-01-15"),
                email = "john@example.com",
                phone = "+441234567890",
                status = PENDING,
            ),
            IndividualEntity(
                individualId = anotherIndividualUid(),
                accountHolderId = someAccountHolderUid(),
                title = "Mrs",
                firstName = "Jane",
                lastName = "Doe",
                dateOfBirth = LocalDate.parse("1992-03-20"),
                email = "jane@example.com",
                phone = "+441234567891",
                status = ACTIVE,
            )
        )
    }

    @Test
    fun findByAccountHolderIdExcludesIndividualsOfOtherAccountHolders() {
        persistIndividual(
            someIndividualUid(),
            anotherAccountHolderUid(),
            null,
            "Other",
            "Person",
            "1985-06-10",
            "other@example.com",
            "+441234567899",
            PENDING
        )

        val result = individualRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isEmpty()
    }

    @Test
    fun findByAccountHolderIdReturnsEmptyListWhenNoIndividualsExist() {
        val result = individualRepository.findByAccountHolderId(someAccountHolderUid())

        assertThat(result).isEmpty()
    }

    private fun persistAccountHolder(accountHolderId: java.util.UUID) {
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

    private fun persistIndividual(
        individualId: java.util.UUID,
        accountHolderId: java.util.UUID,
        title: String?,
        firstName: String,
        lastName: String,
        dateOfBirth: String,
        email: String,
        phone: String,
        status: OnboardingStatus,
    ) {
        persistAndFlush(
            IndividualEntity(
                individualId = individualId,
                accountHolderId = accountHolderId,
                title = title,
                firstName = firstName,
                lastName = lastName,
                dateOfBirth = LocalDate.parse(dateOfBirth),
                email = email,
                phone = phone,
                status = status,
            )
        )
    }
}
