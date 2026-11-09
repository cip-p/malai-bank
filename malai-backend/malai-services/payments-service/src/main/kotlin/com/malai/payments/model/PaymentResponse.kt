package com.malai.payments.model

import com.malai.commons.model.Currency
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.fx.FxConversion
import com.malai.payments.messages.ReturnReason
import java.time.Instant
import java.util.UUID

data class PaymentResponse(
    val paymentId: UUID,
    val state: PaymentState,
    val rail: Rail,
    val direction: PaymentDirection,
    val createdAt: Instant,
    val endToEndId: UUID,
    val fromAccountId: UUID? = null,
    val fromSortCode: String? = null,
    val fromAccountNumber: String? = null,
    val fromName: String? = null,
    val toAccountId: UUID? = null,
    val toSortCode: String? = null,
    val toAccountNumber: String? = null,
    val toName: String? = null,
    val toIban: String? = null,
    val toBic: String? = null,
    val paymentPurpose: String? = null,
    val amountMinorUnits: Long,
    val currency: Currency,
    val description: String? = null,
    val returnReason: ReturnReason? = null,
    val fxConversion: FxConversion? = null,
)
