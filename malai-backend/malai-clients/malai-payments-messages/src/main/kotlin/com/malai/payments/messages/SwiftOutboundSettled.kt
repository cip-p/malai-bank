package com.malai.payments.messages

import java.util.UUID

data class SwiftOutboundSettled(
    val messageType: String = "swift.outbound.settled",
    val paymentId: UUID,
    val endToEndId: UUID,
)
