package com.malai.insights.entity

import com.malai.insights.model.Category
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "category_overrides")
data class CategoryOverrideEntity(

    @Id
    @Column(name = "entry_id")
    val entryId: UUID,

    @Enumerated(STRING)
    val category: Category,

    @Column(name = "created_at")
    val createdAt: Instant,

    @Column(name = "updated_at")
    val updatedAt: Instant,

    @Column(name = "created_by")
    val createdBy: UUID? = null,
)
