package com.malai.aml.service

import com.malai.aml.entity.AmlCaseEntity
import com.malai.aml.exception.AmlCaseAlreadyClosedException
import com.malai.aml.exception.AmlCaseHasAcceptedAlertException
import com.malai.aml.exception.AmlSarNotActionableException
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.repository.AmlCaseRepository
import com.malai.commons.TimeService
import com.malai.commons.config.UuidGenerator
import com.malai.commons.exception.MalaiCode.AML_CASE_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class AmlCaseService(
    private val repository: AmlCaseRepository,
    private val alertGate: AmlAlertGate,
    private val sarGate: AmlSarGate,
    private val auditWriter: AuditWriter,
    private val uuidGenerator: UuidGenerator,
    private val timeService: TimeService,
) {

    fun alertAlreadyRaised(alertDecisionId: UUID): Boolean = repository.linksAlertDecision(alertDecisionId)

    @Transactional
    fun attachAlert(accountId: UUID, alertDecisionId: UUID) {
        val now = timeService.now()
        val openCase = repository.findByAccountIdAndStatusIn(accountId, NON_TERMINAL_STATUSES)
        when {
            openCase == null -> openCase(accountId, alertDecisionId, now)
            alertDecisionId !in openCase.alertDecisionIds -> repository.save(
                openCase.copy(alertDecisionIds = openCase.alertDecisionIds + alertDecisionId, updatedAt = now),
            )
        }
    }

    @Transactional
    fun reconcileFirstOfficerAction(amlCase: AmlCaseEntity, anyAlertRuled: Boolean): AmlCaseEntity =
        if (amlCase.status == AmlCaseStatus.OPEN && anyAlertRuled) {
            repository.save(amlCase.copy(status = AmlCaseStatus.INVESTIGATING, updatedAt = timeService.now()))
        } else {
            amlCase
        }

    fun anOpenCase(caseId: UUID): AmlCaseEntity {
        val amlCase = repository.findById(caseId).orElseThrow { NotFoundException(AML_CASE_NOT_FOUND) }
        if (amlCase.status !in NON_TERMINAL_STATUSES) throw AmlCaseAlreadyClosedException()
        return amlCase
    }

    @Transactional
    fun attachSarDraft(caseId: UUID, sarDecisionId: UUID) {
        val amlCase = repository.findById(caseId).orElseThrow { NotFoundException(AML_CASE_NOT_FOUND) }
        val status =
            if (amlCase.status == AmlCaseStatus.OPEN) AmlCaseStatus.INVESTIGATING else amlCase.status
        repository.save(
            amlCase.copy(status = status, sarDecisionId = sarDecisionId, updatedAt = timeService.now()),
        )
    }

    @Transactional
    fun closeDismissed(caseId: UUID, adminUserId: UUID) {
        val amlCase = repository.findById(caseId).orElseThrow { NotFoundException(AML_CASE_NOT_FOUND) }
        if (amlCase.status !in NON_TERMINAL_STATUSES) throw AmlCaseAlreadyClosedException()
        if (alertGate.anyAlertFindingStands(amlCase.alertDecisionIds)) throw AmlCaseHasAcceptedAlertException()

        val statusBeforeClose = amlCase.status
        val alertDecisionIds = amlCase.alertDecisionIds.map { it.toString() }

        val now = timeService.now()
        repository.save(amlCase.copy(status = AmlCaseStatus.CLOSED_DISMISSED, updatedAt = now, closedAt = now))
        auditWriter.record(
            AuditEvent(
                actorId = adminUserId,
                actorType = ActorType.ADMIN,
                action = "AML_CASE_CLOSED_DISMISSED",
                resourceType = "AmlCase",
                resourceId = caseId.toString(),
                beforeState = mapOf("status" to statusBeforeClose.name),
                afterState = mapOf(
                    "status" to AmlCaseStatus.CLOSED_DISMISSED.name,
                    "alertDecisionIds" to alertDecisionIds,
                ),
            ),
        )
    }

    @Transactional
    fun closeReported(caseId: UUID, adminUserId: UUID) {
        val amlCase = anOpenCase(caseId)
        val sarDecisionId = amlCase.sarDecisionId ?: throw AmlSarNotActionableException()
        val ruled = sarGate.ruledNarrative(sarDecisionId) ?: throw AmlSarNotActionableException()

        val statusBeforeClose = amlCase.status

        val now = timeService.now()
        repository.save(
            amlCase.copy(
                status = AmlCaseStatus.CLOSED_REPORTED,
                sarNarrative = ruled.narrative,
                updatedAt = now,
                closedAt = now,
            ),
        )
        auditWriter.record(
            AuditEvent(
                actorId = adminUserId,
                actorType = ActorType.ADMIN,
                action = "AML_CASE_CLOSED_REPORTED",
                resourceType = "AmlCase",
                resourceId = caseId.toString(),
                beforeState = mapOf("status" to statusBeforeClose.name),
                afterState = mapOf(
                    "status" to AmlCaseStatus.CLOSED_REPORTED.name,
                    "sarDecisionId" to sarDecisionId.toString(),
                    "ruling" to ruled.ruling,
                    "rulingEntryId" to ruled.rulingEntryId.toString(),
                ),
            ),
        )
    }

    private fun openCase(accountId: UUID, alertDecisionId: UUID, now: Instant) {
        val amlCase = AmlCaseEntity(
            caseId = uuidGenerator.generate(),
            accountId = accountId,
            status = AmlCaseStatus.OPEN,
            alertDecisionIds = listOf(alertDecisionId),
            openedAt = now,
            updatedAt = now,
        )
        repository.save(amlCase)
        auditWriter.record(
            AuditEvent(
                actorId = null,
                actorType = ActorType.SYSTEM,
                action = "AML_CASE_OPENED",
                resourceType = "AmlCase",
                resourceId = amlCase.caseId.toString(),
                afterState = mapOf(
                    "accountId" to accountId.toString(),
                    "status" to AmlCaseStatus.OPEN.name,
                    "alertDecisionIds" to listOf(alertDecisionId.toString()),
                ),
            ),
        )
    }

    companion object {
        val NON_TERMINAL_STATUSES = setOf(AmlCaseStatus.OPEN, AmlCaseStatus.INVESTIGATING)
    }
}
