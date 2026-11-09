package com.malai.client.malai.accountholder.onboarding.model

import java.util.UUID

data class CreateOnboardingAccountHolderRequest(val userId: UUID)

data class CreateOnboardingAccountHolderResponse(val accountHolderId: UUID)

data class CreateOnboardingIndividualRequest(
    val firstName: String,
    val lastName: String,
    /** ISO-8601 yyyy-MM-dd. Kept as a String here to avoid wiring JSR-310 into the client object mapper. */
    val dateOfBirth: String,
    val email: String,
)

data class CreateOnboardingAddressRequest(
    val line1: String,
    val line2: String?,
    val city: String,
    val postcode: String,
    val country: String,
)
