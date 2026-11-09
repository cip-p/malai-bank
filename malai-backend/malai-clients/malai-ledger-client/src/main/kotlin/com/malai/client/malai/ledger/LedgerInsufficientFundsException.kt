package com.malai.client.malai.ledger

/**
 * Thrown by [LedgerClient.postTransaction] when the ledger rejects a posting because it would
 * take a guarded account's Available balance below its floor (the ledger's `LEDGER_INSUFFICIENT_FUNDS`
 * rejection). Callers map this to their own domain-facing response — the funds guard lives in the
 * ledger, so callers never pre-check.
 */
class LedgerInsufficientFundsException(responseBody: String?) :
    RuntimeException("ledger rejected posting: insufficient funds (${responseBody ?: "<empty>"})")
