package com.malai.client.malai.payments

/**
 * Thrown by [MalaiPaymentsClient.initiateScheduledPayment] when payments-service rejects a
 * scheduled initiation because the payer cannot cover the amount (the `PAYMENT_INSUFFICIENT_FUNDS`
 * rejection). A scheduler maps this to a missed occurrence — audited, never retried — rather than
 * a hard failure of the run.
 */
class PaymentInsufficientFundsException(responseBody: String?) :
    RuntimeException("payments-service rejected initiation: insufficient funds (${responseBody ?: "<empty>"})")
