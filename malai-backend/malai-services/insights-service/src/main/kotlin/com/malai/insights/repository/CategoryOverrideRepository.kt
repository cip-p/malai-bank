package com.malai.insights.repository

import com.malai.insights.entity.CategoryOverrideEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CategoryOverrideRepository : JpaRepository<CategoryOverrideEntity, UUID>
