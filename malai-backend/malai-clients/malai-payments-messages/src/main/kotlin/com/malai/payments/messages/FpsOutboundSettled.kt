package com.malai.payments.messages

import java.util.UUID

data class FpsOutboundSettled(
    val messageType: String = "fps.outbound.settled",
    val paymentId: UUID,
    val endToEndId: UUID,
)
