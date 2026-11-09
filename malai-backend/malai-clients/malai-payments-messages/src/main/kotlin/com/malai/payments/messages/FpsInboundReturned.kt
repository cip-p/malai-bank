package com.malai.payments.messages

import java.util.UUID

data class FpsInboundReturned(
    val messageType: String = "fps.inbound.returned",
    val endToEndId: UUID,
    val beneficiarySortCode: String,
    val beneficiaryAccountNumber: String,
    val reason: ReturnReason,
)
