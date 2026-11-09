package com.malai.aml.service

import com.malai.aml.entity.AmlCaseEntity
import com.malai.aml.sar.SarTimeline
import com.malai.aml.sar.SarTimelineComposer
import com.malai.security.audit.AuditReader
import org.springframework.stereotype.Service

@Service
class SarTimelineAssembler(
    private val alertAssembler: AmlAlertAssembler,
    private val auditReader: AuditReader,
) {

    fun assemble(amlCase: AmlCaseEntity): SarTimeline = SarTimelineComposer.compose(
        caseId = amlCase.caseId,
        accountId = amlCase.accountId,
        openedAt = amlCase.openedAt,
        alerts = alertAssembler.assembleAlerts(amlCase.alertDecisionIds),
        auditEvents = auditReader.eventsFor(
            SarTimelineComposer.CASE_RESOURCE_TYPE,
            amlCase.caseId.toString(),
        ),
    )
}
