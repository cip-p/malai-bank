package com.malai.accounts.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

/**
 * Request bodies for the service-to-service onboarding endpoints. The auth-service's register
 * orchestrator calls these — see `malai-docs/webapp-flows/onboarding.md`.
 */

data class CreateOnboardingAccountRequest(

    @field:NotNull
    val userId: UUID?,

    @field:NotNull
    val accountHolderId: UUID?,

    @field:NotBlank
    @field:Size(max = 10)
    val currency: String?,

    @field:NotBlank
    @field:Size(max = 255)
    val name: String?,
)

data class CreateOnboardingAccountResponse(
    val accountId: UUID,
)
