package com.malai.accounts.model

data class ConfirmationOfFundsResponse(
    val requestedAmountAvailableToSpend: Boolean,
    val accountWouldBeInOverdraftIfRequestedAmountSpent: Boolean
)
