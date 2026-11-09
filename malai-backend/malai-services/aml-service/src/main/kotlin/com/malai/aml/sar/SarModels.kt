package com.malai.aml.sar

import com.malai.aml.monitoring.sha256Hex
import java.time.Instant
import java.util.UUID

enum class SarRecordType(val timelineRank: Int) {
    PAYMENT(0),
    ALERT_DECISION(1),
    AUDIT_EVENT(2),
}

data class SarRef(val type: String, val id: String)

data class SarRecord(
    val type: SarRecordType,
    val id: String,
    val moment: Instant,
    val summary: String,
    val contentHash: String = sha256Hex("${type.name}|$id|$moment|$summary"),
) {
    val ref: SarRef = SarRef(type.name, id)
}

data class RuledSarNarrative(
    val ruling: String,
    val rulingEntryId: UUID,
    val narrative: String,
)

data class SarTimeline(
    val caseId: UUID,
    val accountId: UUID,
    val openedAt: Instant,
    val records: List<SarRecord>,
    val evidenceAmountsMinorUnits: List<Long> = emptyList(),
) {
    private val refs: Set<SarRef> = records.map { it.ref }.toSet()

    fun admits(ref: SarRef): Boolean = ref in refs
}
