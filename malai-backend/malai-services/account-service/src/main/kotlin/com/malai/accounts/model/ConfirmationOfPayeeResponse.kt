package com.malai.accounts.model

enum class ConfirmationOfPayeeResult { MATCH, CLOSE_MATCH, NO_MATCH }

data class ConfirmationOfPayeeResponse(
    val result: ConfirmationOfPayeeResult,
    val onRecordName: String? = null,
)
