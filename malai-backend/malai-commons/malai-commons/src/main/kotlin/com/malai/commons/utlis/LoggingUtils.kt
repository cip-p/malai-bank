package com.malai.commons.utlis

import org.slf4j.MDC
import java.util.*

class LoggingUtils {

    companion object {
        // BACK-10 contract: MDC keys this helper owns. Cleanup MUST be key-scoped
        // (not `MDC.clear()`) so upstream-set keys — most importantly `trace_id`
        // and `span_id` from the OTel Java agent's logback-mdc instrumentation —
        // survive the request lifecycle. The audit writer + ledger reader both
        // read `MDC.get("trace_id")` further down the request chain; an MDC.clear()
        // here silently zeroes audit_events.trace_id and ledger_entries.trace_id.
        private const val USER_ID_KEY = "userId"
        private const val ACCOUNT_HOLDER_ID_KEY = "accountHolderId"

        fun addUserToLoggingContext(userId: UUID) {
            addToLoggingContext(USER_ID_KEY, userId.toString())
        }

        fun addAccountHolderToLoggingContext(accountHolderId: UUID) {
            addToLoggingContext(ACCOUNT_HOLDER_ID_KEY, accountHolderId.toString())
        }

        // Removes the keys this helper sets. Trace + span ids (OTel) and any other
        // upstream-owned MDC are intentionally left untouched.
        fun clearLoggingContext() {
            MDC.remove(USER_ID_KEY)
            MDC.remove(ACCOUNT_HOLDER_ID_KEY)
        }

        private fun addToLoggingContext(key: String, value: String) {
            MDC.put(key, value)
        }
    }

}
