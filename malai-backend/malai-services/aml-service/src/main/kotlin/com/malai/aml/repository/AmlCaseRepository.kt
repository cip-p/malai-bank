package com.malai.aml.repository

import com.malai.aml.entity.AmlCaseEntity
import com.malai.aml.model.AmlCaseStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface AmlCaseRepository : JpaRepository<AmlCaseEntity, UUID> {

    fun findByAccountIdAndStatusIn(accountId: UUID, statuses: Collection<AmlCaseStatus>): AmlCaseEntity?

    @Query(
        value = "SELECT EXISTS(SELECT 1 FROM aml_cases WHERE :decisionId = ANY(alert_decision_ids))",
        nativeQuery = true,
    )
    fun linksAlertDecision(@Param("decisionId") decisionId: UUID): Boolean

    fun findAllByOrderByOpenedAtDesc(): List<AmlCaseEntity>

    fun findByStatusOrderByOpenedAtDesc(status: AmlCaseStatus): List<AmlCaseEntity>
}
