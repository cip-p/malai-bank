package com.malai.aml.service

import com.malai.aml.entity.AmlCaseEntity
import com.malai.aml.model.AmlAlertView
import com.malai.aml.model.AmlCaseDetailResponse
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.model.AmlCaseSummariesResponse
import com.malai.aml.model.AmlCaseSummary
import com.malai.aml.repository.AmlCaseRepository
import com.malai.commons.exception.MalaiCode.AML_CASE_NOT_FOUND
import com.malai.commons.exception.NotFoundException
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AmlCaseReadService(
    private val repository: AmlCaseRepository,
    private val alertAssembler: AmlAlertAssembler,
    private val caseService: AmlCaseService,
) {

    fun listCases(status: AmlCaseStatus?): AmlCaseSummariesResponse {
        val cases = status
            ?.let { repository.findByStatusOrderByOpenedAtDesc(it) }
            ?: repository.findAllByOrderByOpenedAtDesc()
        return AmlCaseSummariesResponse(cases.map { it.toSummary() })
    }

    fun caseDetail(caseId: UUID): AmlCaseDetailResponse {
        val amlCase = repository.findById(caseId).orElseThrow { NotFoundException(AML_CASE_NOT_FOUND) }
        val alerts = alertAssembler.assembleAlerts(amlCase.alertDecisionIds)
        val reconciled = caseService.reconcileFirstOfficerAction(amlCase, alerts.any { it.rulingInForce != null })
        return reconciled.toDetail(alerts)
    }

    private fun AmlCaseEntity.toSummary() = AmlCaseSummary(
        caseId = caseId,
        accountId = accountId,
        status = status,
        alertCount = alertDecisionIds.size,
        openedAt = openedAt,
        updatedAt = updatedAt,
    )

    private fun AmlCaseEntity.toDetail(alerts: List<AmlAlertView>) = AmlCaseDetailResponse(
        caseId = caseId,
        accountId = accountId,
        status = status,
        openedAt = openedAt,
        updatedAt = updatedAt,
        alerts = alerts,
        sar = sarDecisionId?.let { alertAssembler.assembleSar(it, sarNarrative) },
    )
}
