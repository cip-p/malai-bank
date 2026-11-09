package com.malai.aml.entity

import com.malai.aml.model.AmlCaseStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "aml_cases")
data class AmlCaseEntity(

    @Id
    @Column(name = "case_id", nullable = false)
    val caseId: UUID,

    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    val status: AmlCaseStatus,

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "alert_decision_ids", nullable = false, columnDefinition = "uuid[]")
    val alertDecisionIds: List<UUID>,

    @Column(name = "sar_decision_id")
    val sarDecisionId: UUID? = null,

    @Column(name = "sar_narrative")
    val sarNarrative: String? = null,

    @Column(name = "opened_at", nullable = false)
    val openedAt: Instant,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant,

    @Column(name = "closed_at")
    val closedAt: Instant? = null,
)
