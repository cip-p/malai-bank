package com.malai.client.malai.stub.directory.model

import com.malai.payments.messages.ReturnReason

data class CounterpartiesResponse(val counterparties: List<CounterpartyResponse>)

data class CounterpartyResponse(
    val sortCode: String,
    val accountNumber: String,
    val bank: String,
    val nameOnRecord: String,
    val outboundOutcome: OutboundOutcome,
    val returnReason: ReturnReason? = null,
)

enum class OutboundOutcome { SETTLES, RETURNS, STUCK_PENDING }
