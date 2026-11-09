package com.malai.accountholder.service

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.AddressEntity
import com.malai.accountholder.entity.IndividualEntity
import com.malai.accountholder.exception.AccountHolderNotFoundException
import com.malai.accountholder.model.CreateOnboardingAccountHolderRequest
import com.malai.accountholder.model.CreateOnboardingAccountHolderResponse
import com.malai.accountholder.model.CreateOnboardingAddressRequest
import com.malai.accountholder.model.CreateOnboardingIndividualRequest
import com.malai.accountholder.repository.AccountHolderRepository
import com.malai.accountholder.repository.AddressRepository
import com.malai.accountholder.repository.IndividualRepository
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.model.OnboardingStatus
import com.malai.commons.model.OnboardingStatus.PENDING
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*

/**
 * Backs the service-to-service onboarding endpoints. Each create-* method writes one row in
 * `PENDING` status; [activate] flips the holder + its individual to `ACTIVE` once the user has
 * confirmed their email upstream.
 *
 * The auth-service's register orchestrator is the only expected caller — see
 * `malai-docs/webapp-flows/onboarding.md`.
 */
// todo: this class overlaps with AccountHolderService; consider merging them at a later stage in the project;
@Service
class OnboardingService(
    private val accountHolderRepository: AccountHolderRepository,
    private val individualRepository: IndividualRepository,
    private val addressRepository: AddressRepository,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
    private val auditWriter: AuditWriter,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun createAccountHolder(
        request: CreateOnboardingAccountHolderRequest,
    ): CreateOnboardingAccountHolderResponse {
        val entity = AccountHolderEntity(
            accountHolderId = uuidGenerator.generate(),
            userId = request.userId!!,
            accountHolderType = AccountHolderType.INDIVIDUAL,
            createdAt = timeService.now(),
            status = PENDING,
        )
        accountHolderRepository.save(entity)
        auditWriter.record(
            AuditEvent(
                actorId = request.userId,
                actorType = ActorType.SYSTEM,
                action = "ACCOUNT_HOLDER_ONBOARDED",
                resourceType = "AccountHolder",
                resourceId = entity.accountHolderId.toString(),
                afterState = mapOf("status" to entity.status.name),
            )
        )
        log.info("onboarding: created account-holder ${entity.accountHolderId} (PENDING)")
        return CreateOnboardingAccountHolderResponse(entity.accountHolderId)
    }

    fun createIndividual(accountHolderId: UUID, request: CreateOnboardingIndividualRequest) {
        requireAccountHolder(accountHolderId)
        val saved = individualRepository.save(
            IndividualEntity(
                individualId = uuidGenerator.generate(),
                accountHolderId = accountHolderId,
                title = null,
                firstName = request.firstName!!,
                lastName = request.lastName!!,
                dateOfBirth = request.dateOfBirth!!,
                email = request.email!!,
                phone = "",
                status = PENDING,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "INDIVIDUAL_ONBOARDED",
                resourceType = "Individual",
                resourceId = saved.individualId.toString(),
                afterState = mapOf("accountHolderId" to accountHolderId.toString(), "status" to saved.status.name),
            )
        )
        log.info("onboarding: created individual for account-holder $accountHolderId (PENDING)")
    }

    fun createAddress(accountHolderId: UUID, request: CreateOnboardingAddressRequest) {
        requireAccountHolder(accountHolderId)
        val today = LocalDate.ofInstant(timeService.now(), ZoneOffset.UTC)
        val saved = addressRepository.save(
            AddressEntity(
                addressId = uuidGenerator.generate(),
                accountHolderId = accountHolderId,
                line1 = request.line1!!,
                line2 = request.line2,
                line3 = null,
                postTown = request.city!!,
                postCode = request.postcode!!,
                countryCode = request.country!!.uppercase(),
                udprn = null,
                umprn = null,
                fromDate = today,
                isCurrent = true,
            )
        )
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "ADDRESS_ONBOARDED",
                resourceType = "AccountHolder",
                resourceId = accountHolderId.toString(),
                afterState = mapOf("addressId" to saved.addressId.toString(), "postCode" to request.postcode),
            )
        )
        log.info("onboarding: created address for account-holder $accountHolderId")
    }

    @Transactional
    fun activateByUserId(userId: UUID) {
        val activated = mutableListOf<UUID>()
        accountHolderRepository.findByUserId(userId).forEach { holder ->
            accountHolderRepository.save(holder.copy(status = OnboardingStatus.ACTIVE))
            activated += holder.accountHolderId
            individualRepository.findByAccountHolderId(holder.accountHolderId).forEach { individual ->
                individualRepository.save(individual.copy(status = OnboardingStatus.ACTIVE))
            }
        }
        auditWriter.record(
            AuditEvent(
                actorId = userId,
                actorType = ActorType.SYSTEM,
                action = "ACCOUNT_HOLDERS_ACTIVATED",
                resourceType = "User",
                resourceId = userId.toString(),
                afterState = mapOf("activatedAccountHolderIds" to activated.map { it.toString() }),
            )
        )
        log.info("onboarding: activated account-holder(s) for user $userId")
    }

    private fun requireAccountHolder(accountHolderId: UUID): AccountHolderEntity =
        accountHolderRepository.findById(accountHolderId).orElseThrow { AccountHolderNotFoundException() }
}
