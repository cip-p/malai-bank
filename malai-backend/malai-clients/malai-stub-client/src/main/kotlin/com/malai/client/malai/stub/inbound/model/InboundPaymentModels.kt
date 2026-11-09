package com.malai.client.malai.stub.inbound.model

enum class SandboxRail { FPS, BACS }

data class InboundPaymentForwardPayload(
    val beneficiarySortCode: String,
    val beneficiaryAccountNumber: String,
    val beneficiaryName: String,
    val originatorSortCode: String,
    val originatorAccountNumber: String,
    val originatorName: String,
    val amountMinorUnits: Long,
    val currency: String,
    val description: String?,
)
