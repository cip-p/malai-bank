package com.malai.client.malai.accounts.onboarding.model

import java.util.UUID

data class CreateOnboardingAccountRequest(
    val userId: UUID,
    val accountHolderId: UUID,
    val currency: String,
    val name: String,
)

data class CreateOnboardingAccountResponse(val accountId: UUID)
