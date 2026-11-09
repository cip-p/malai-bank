package com.malai.payments.messages

import java.util.UUID

data class FpsOutboundReturned(
    val messageType: String = "fps.outbound.returned",
    val paymentId: UUID,
    val endToEndId: UUID,
    val reason: ReturnReason,
)
