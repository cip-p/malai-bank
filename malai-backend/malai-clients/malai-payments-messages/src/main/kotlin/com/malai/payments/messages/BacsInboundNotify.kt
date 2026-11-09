package com.malai.payments.messages

import java.util.UUID

data class BacsInboundNotify(
    val messageType: String = "bacs.inbound.notify",
    override val endToEndId: UUID,
    override val beneficiarySortCode: String,
    override val beneficiaryAccountNumber: String,
    override val beneficiaryName: String,
    override val originatorSortCode: String,
    override val originatorAccountNumber: String,
    override val originatorName: String,
    override val amountMinorUnits: Long,
    override val currency: String,
    override val description: String?,
) : InboundNotify
