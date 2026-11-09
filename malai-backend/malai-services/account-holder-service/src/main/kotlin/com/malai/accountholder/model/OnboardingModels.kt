package com.malai.accountholder.model

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.*

/**
 * Request bodies for the service-to-service onboarding endpoints. The auth-service's register
 * orchestrator calls these during the V1 onboarding flow — see `malai-docs/webapp-flows/onboarding.md`.
 */

data class CreateOnboardingAccountHolderRequest(
    @field:NotNull
    val userId: UUID?,
)

data class CreateOnboardingAccountHolderResponse(
    val accountHolderId: UUID,
)

data class CreateOnboardingIndividualRequest(

    @field:NotBlank
    @field:Size(max = 100)
    val firstName: String?,

    @field:NotBlank
    @field:Size(max = 100)
    val lastName: String?,

    @field:NotNull
    val dateOfBirth: LocalDate?,

    @field:NotBlank
    val email: String?,
)

data class CreateOnboardingAddressRequest(

    @field:NotBlank
    @field:Size(max = 200)
    val line1: String?,

    @field:Size(max = 200)
    val line2: String?,

    @field:NotBlank
    @field:Size(max = 100)
    val city: String?,

    @field:NotBlank
    @field:Size(max = 20)
    val postcode: String?,

    @field:NotBlank
    @field:Size(min = 2, max = 2)
    val country: String?,
)
