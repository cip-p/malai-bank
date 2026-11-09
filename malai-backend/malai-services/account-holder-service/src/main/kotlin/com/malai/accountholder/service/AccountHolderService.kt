package com.malai.accountholder.service

import com.malai.accountholder.entity.*
import com.malai.accountholder.entity.AccountHolderType.*
import com.malai.accountholder.exception.AccountHolderNotFoundException
import com.malai.accountholder.exception.SettleUpProfileNotFoundException
import com.malai.accountholder.model.*
import com.malai.accountholder.repository.*
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.OnboardingStatus.ACTIVE
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.*

@Service
class AccountHolderService(
    private val accountHolderRepository: AccountHolderRepository,
    private val individualRepository: IndividualRepository,
    private val businessRepository: BusinessRepository,
    private val businessAddressRepository: BusinessAddressRepository,
    private val soleTraderRepository: SoleTraderRepository,
    private val profileImageRepository: ProfileImageRepository,
    private val addressRepository: AddressRepository,
    private val settleUpProfileRepository: SettleUpProfileRepository,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
    private val auditWriter: AuditWriter,
) {

    fun getAllAccountHolders(userId: UUID): AccountHoldersResponse {
        val accountHolders = accountHolderRepository.findByUserId(userId)

        val summaries = accountHolders.map { entity ->
            AccountHolderSummaryResponse(
                accountHolderUid = entity.accountHolderId,
                accountHolderName = resolveAccountHolderName(entity.accountHolderId, entity.accountHolderType),
                accountHolderType = entity.accountHolderType.name
            )
        }

        return AccountHoldersResponse(summaries)
    }

    private fun resolveAccountHolderName(accountHolderUid: UUID): String {
        val accountHolder = accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        return resolveAccountHolderName(accountHolderUid, accountHolder.accountHolderType)
    }

    private fun resolveAccountHolderName(accountHolderUid: UUID, accountHolderType: AccountHolderType): String {
        return when (accountHolderType) {
            INDIVIDUAL, JOINT -> {
                val individual = individualRepository.findByAccountHolderId(accountHolderUid).firstOrNull()
                    ?: throw AccountHolderNotFoundException()
                "${individual.firstName} ${individual.lastName}"
            }

            BUSINESS, BANKING_AS_A_SERVICE -> {
                val business = businessRepository.findByAccountHolderId(accountHolderUid)
                    ?: throw AccountHolderNotFoundException()
                business.companyName
            }

            SOLE_TRADER -> {
                val soleTrader = soleTraderRepository.findByAccountHolderId(accountHolderUid)
                    ?: throw AccountHolderNotFoundException()
                soleTrader.tradingAsName
            }
        }
    }

    fun getAccountHolder(accountHolderUid: UUID): AccountHolderResponse {
        val entity = accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        return AccountHolderResponse(
            accountHolderUid = entity.accountHolderId,
            accountHolderType = entity.accountHolderType.name
        )
    }

    fun createAccountHolder(userId: UUID, request: CreateAccountHolderRequest): AccountHolderResponse {
        val entity = accountHolderRepository.save(
            AccountHolderEntity(
                accountHolderId = uuidGenerator.generate(),
                userId = userId,
                accountHolderType = AccountHolderType.valueOf(request.accountHolderType),
                createdAt = timeService.now(),
                status = ACTIVE
            )
        )

        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.CUSTOMER,
                action = "ACCOUNT_HOLDER_CREATED",
                resourceType = "AccountHolder",
                resourceId = entity.accountHolderId.toString(),
                afterState = mapOf(
                    "accountHolderType" to entity.accountHolderType.name,
                    "status" to entity.status.name,
                ),
            )
        )

        return AccountHolderResponse(
            accountHolderUid = entity.accountHolderId,
            accountHolderType = entity.accountHolderType.name
        )
    }

    fun getAccountHolderName(accountHolderUid: UUID): AccountHolderNameResponse {
        return AccountHolderNameResponse(resolveAccountHolderName(accountHolderUid))
    }

    fun getIndividual(accountHolderUid: UUID): IndividualResponse {
        val individual = individualRepository.findByAccountHolderId(accountHolderUid).firstOrNull()
            ?: throw AccountHolderNotFoundException()

        return toIndividualResponse(individual)
    }

    fun createIndividual(accountHolderUid: UUID, request: CreateIndividualRequest): IndividualResponse {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        val entity = individualRepository.save(
            IndividualEntity(
                individualId = uuidGenerator.generate(),
                accountHolderId = accountHolderUid,
                title = request.title,
                firstName = request.firstName,
                lastName = request.lastName,
                dateOfBirth = LocalDate.parse(request.dateOfBirth),
                email = request.email,
                phone = request.phone,
                status = ACTIVE,
            )
        )

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.CUSTOMER,
                action = "INDIVIDUAL_CREATED",
                resourceType = "Individual",
                resourceId = entity.individualId.toString(),
                afterState = mapOf("accountHolderId" to accountHolderUid.toString()),
            )
        )

        return toIndividualResponse(entity)
    }

    fun updateEmail(accountHolderUid: UUID, request: UpdateEmailRequest) {
        val individual = individualRepository.findByAccountHolderId(accountHolderUid).firstOrNull()
            ?: throw AccountHolderNotFoundException()

        val updated = IndividualEntity(
            individualId = individual.individualId,
            accountHolderId = individual.accountHolderId,
            title = individual.title,
            firstName = individual.firstName,
            lastName = individual.lastName,
            dateOfBirth = individual.dateOfBirth,
            email = request.email,
            phone = individual.phone,
            status = individual.status,
        )
        individualRepository.save(updated)

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.CUSTOMER,
                action = "INDIVIDUAL_EMAIL_UPDATED",
                resourceType = "Individual",
                resourceId = individual.individualId.toString(),
                beforeState = mapOf("email" to individual.email),
                afterState = mapOf("email" to request.email),
            )
        )
    }

    fun getJointAccount(accountHolderUid: UUID): JointAccountResponse {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        val individuals = individualRepository.findByAccountHolderId(accountHolderUid)
        if (individuals.size < 2) {
            throw AccountHolderNotFoundException()
        }

        return JointAccountResponse(
            accountHolderUid = accountHolderUid,
            personOne = toIndividualResponse(individuals[0]),
            personTwo = toIndividualResponse(individuals[1])
        )
    }

    fun getBusiness(accountHolderUid: UUID): BusinessResponse {
        val business = businessRepository.findByAccountHolderId(accountHolderUid)
            ?: throw AccountHolderNotFoundException()

        return BusinessResponse(
            companyName = business.companyName,
            companyType = business.companyType,
            companyCategory = business.companyCategory,
            companySubCategory = business.companySubCategory,
            companyRegistrationNumber = business.companyRegistrationNumber,
            email = business.email,
            phone = business.phone
        )
    }

    fun getBusinessCorrespondenceAddress(accountHolderUid: UUID): AddressResponse {
        val business = businessRepository.findByAccountHolderId(accountHolderUid)
            ?: throw AccountHolderNotFoundException()

        val address = businessAddressRepository.findByBusinessIdAndAddressType(business.businessId, AddressType.CORRESPONDENCE)
            ?: throw AccountHolderNotFoundException()

        return AddressResponse(
            line1 = address.line1,
            line2 = address.line2,
            line3 = address.line3,
            postTown = address.postTown,
            postCode = address.postCode,
            countryCode = address.countryCode
        )
    }

    fun getBusinessRegisteredAddress(accountHolderUid: UUID): AddressResponse {
        val business = businessRepository.findByAccountHolderId(accountHolderUid)
            ?: throw AccountHolderNotFoundException()

        val address = businessAddressRepository.findByBusinessIdAndAddressType(business.businessId, AddressType.REGISTERED)
            ?: throw AccountHolderNotFoundException()

        return AddressResponse(
            line1 = address.line1,
            line2 = address.line2,
            line3 = address.line3,
            postTown = address.postTown,
            postCode = address.postCode,
            countryCode = address.countryCode
        )
    }

    fun getSoleTrader(accountHolderUid: UUID): SoleTraderResponse {
        val soleTrader = soleTraderRepository.findByAccountHolderId(accountHolderUid)
            ?: throw AccountHolderNotFoundException()

        return SoleTraderResponse(
            tradingAsName = soleTrader.tradingAsName,
            businessCategory = soleTrader.businessCategory,
            businessSubCategory = soleTrader.businessSubCategory
        )
    }

    fun getProfileImage(accountHolderUid: UUID): ProfileImage {
        val entity = profileImageRepository.findByAccountHolderId(accountHolderUid)
            ?: throw AccountHolderNotFoundException()
        return ProfileImage(contentType = entity.contentType, imageData = entity.imageData)
    }

    fun updateProfileImage(accountHolderUid: UUID, contentType: String, imageData: ByteArray) {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        val existing = profileImageRepository.findByAccountHolderId(accountHolderUid)
        if (existing != null) {
            val updated = ProfileImageEntity(
                profileImageId = existing.profileImageId,
                accountHolderId = accountHolderUid,
                contentType = contentType,
                imageData = imageData
            )
            profileImageRepository.save(updated)
        } else {
            profileImageRepository.save(
                ProfileImageEntity(
                    profileImageId = uuidGenerator.generate(),
                    accountHolderId = accountHolderUid,
                    contentType = contentType,
                    imageData = imageData
                )
            )
        }

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.CUSTOMER,
                action = "PROFILE_IMAGE_UPDATED",
                resourceType = "AccountHolder",
                resourceId = accountHolderUid.toString(),
                afterState = mapOf("contentType" to contentType, "size" to imageData.size),
            )
        )
    }

    @Transactional
    fun deleteProfileImage(accountHolderUid: UUID) {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        profileImageRepository.deleteByAccountHolderId(accountHolderUid)

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.CUSTOMER,
                action = "PROFILE_IMAGE_DELETED",
                resourceType = "AccountHolder",
                resourceId = accountHolderUid.toString(),
            )
        )
    }

    fun getAddresses(accountHolderUid: UUID): AddressesResponse {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        val current = addressRepository.findByAccountHolderIdAndIsCurrent(accountHolderUid, true)
        val previous = addressRepository.findByAccountHolderIdAndIsCurrentFalseOrderByFromDateDesc(accountHolderUid)

        return AddressesResponse(
            current = current?.let { toAddressResponse(it) },
            previous = previous.map { toAddressResponse(it) }
        )
    }

    fun updateAddress(accountHolderUid: UUID, request: AddressUpdateRequest) {
        accountHolderRepository.findById(accountHolderUid)
            .orElseThrow { AccountHolderNotFoundException() }

        val existingCurrent = addressRepository.findByAccountHolderIdAndIsCurrent(accountHolderUid, true)
        if (existingCurrent != null) {
            val demoted = AddressEntity(
                addressId = existingCurrent.addressId,
                accountHolderId = existingCurrent.accountHolderId,
                line1 = existingCurrent.line1,
                line2 = existingCurrent.line2,
                line3 = existingCurrent.line3,
                postTown = existingCurrent.postTown,
                postCode = existingCurrent.postCode,
                countryCode = existingCurrent.countryCode,
                udprn = existingCurrent.udprn,
                umprn = existingCurrent.umprn,
                fromDate = existingCurrent.fromDate,
                isCurrent = false
            )
            addressRepository.save(demoted)
        }

        val saved = addressRepository.save(
            AddressEntity(
                addressId = uuidGenerator.generate(),
                accountHolderId = accountHolderUid,
                line1 = request.line1,
                line2 = request.line2,
                line3 = request.line3,
                postTown = request.postTown,
                postCode = request.postCode,
                countryCode = request.countryCode,
                udprn = request.udprn,
                umprn = request.umprn,
                fromDate = LocalDate.parse(request.from),
                isCurrent = true
            )
        )

        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.CUSTOMER,
                action = "ADDRESS_UPDATED",
                resourceType = "AccountHolder",
                resourceId = accountHolderUid.toString(),
                afterState = mapOf("addressId" to saved.addressId.toString(), "postCode" to request.postCode),
            )
        )
    }

    fun getSettleUpProfile(accountHolderUid: UUID): SettleUpProfileResponse {
        val profile = settleUpProfileRepository.findByAccountHolderId(accountHolderUid)
            ?: throw SettleUpProfileNotFoundException()

        return SettleUpProfileResponse(
            settleUpLink = profile.settleUpLink,
            status = profile.status.name
        )
    }

    private fun toAddressResponse(entity: AddressEntity): AddressResponse {
        return AddressResponse(
            line1 = entity.line1,
            line2 = entity.line2,
            line3 = entity.line3,
            postTown = entity.postTown,
            postCode = entity.postCode,
            countryCode = entity.countryCode
        )
    }

    private fun toIndividualResponse(entity: IndividualEntity): IndividualResponse {
        return IndividualResponse(
            title = entity.title,
            firstName = entity.firstName,
            lastName = entity.lastName,
            dateOfBirth = entity.dateOfBirth.toString(),
            email = entity.email,
            phone = entity.phone
        )
    }
}
