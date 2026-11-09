package com.malai.copilot.model

import java.time.Instant
import java.util.UUID

enum class SourceRecordType(val timelineRank: Int) {
    PAYMENT(0),
    HOLD(1),
    SCREENING_DECISION(2),
    AUDIT_EVENT(3),
    LEDGER_ENTRY(4),
}

data class SourceRecord(
    val type: SourceRecordType,
    val id: String,
    val moment: Instant,
    val summary: String,
) {
    val contentHash: String = sha256Hex("${type.name}|$id|$moment|$summary")

    val ref: SourceRef = SourceRef(type.name, id)
}

data class SourceRef(val type: String, val id: String)

data class TimelineBalances(
    val accountId: UUID,
    val ledgerBalanceMinorUnits: Long,
    val heldAmountMinorUnits: Long,
    val availableBalanceMinorUnits: Long,
)

data class Timeline(
    val subjectType: String,
    val subjectId: UUID,
    val records: List<SourceRecord>,
    val balances: TimelineBalances? = null,
    val subjectAmountMinorUnits: Long? = null,
) {
    private val refs: Set<SourceRef> = records.map { it.ref }.toSet()

    fun admits(ref: SourceRef): Boolean = ref in refs
}
