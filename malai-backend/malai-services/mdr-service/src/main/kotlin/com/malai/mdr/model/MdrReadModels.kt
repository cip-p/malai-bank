package com.malai.mdr.model

import java.util.UUID

// Response for the gate: GET /api/v1/admin/mdr/decisions/{decisionId}/actionable-output. Served
// only while the Ruling in force is ACCEPT (the Raw output) or EDIT (the stored edited output
// verbatim); REJECT or no ruling refuses with 409. `rulingEntryId` names the Disposition Entry
// that authorised the output, so a consumer can cite exactly which ruling it acted under.
data class MdrActionableOutputResponse(
    val decisionId: UUID,
    val ruling: String,
    val rulingEntryId: UUID,
    val actionableOutput: String,
)

// Filter vocabulary for the Decision worklist: AWAITING_RULING selects Decisions with no
// Disposition yet (the reviewer's default view); ACCEPT / EDIT / REJECT select by the Ruling in
// force -- the latest Disposition's kind, exactly as the gate evaluates it.
enum class MdrRulingState {
    AWAITING_RULING,
    ACCEPT,
    EDIT,
    REJECT,
}

// Response for the Decision worklist: GET /api/v1/admin/mdr/decisions. One summary per Model
// Decision, newest first (descending chain seq), so a reviewer can find work without knowing a
// decision id. `rulingInForce` is absent while the Decision awaits its first ruling; `advisory`
// says whether a ruling is expected at all (ADR-0013), so the reviewer's default view is the
// non-advisory decisions still awaiting one.
data class MdrDecisionSummariesResponse(
    val decisions: List<MdrDecisionSummaryView>,
)

data class MdrDecisionSummaryView(
    val decisionId: UUID,
    val entryId: UUID,
    val seq: Long,
    val ts: String,
    val outcome: String,
    val modelId: String,
    val modelVersion: String,
    val correlationType: String,
    val correlationId: String,
    val advisory: Boolean,
    val dispositionCount: Long,
    val latencyMs: Long,
    val costMicros: Long,
    val costCurrency: String,
    val rulingInForce: String?,
    val verdict: Map<String, Any?>?,
)

// Response for the service-to-service record read: GET /api/v1/internal/mdr/decisions/{decisionId}.
// The Model Decision as recorded -- its Correlation ref, Outcome, Verdict and Retrieved-context refs
// -- plus `rulingInForce`, the derived state a consuming view renders. Deliberately not the replay
// thread: no Disposition entries and no Raw output, because ADR-0009 keeps the accept/edit/reject ->
// actionable-output translation inside the gate. A consumer displays the ruling; it acts only on
// what /actionable-output serves it.
data class MdrDecisionRecordResponse(
    val decisionId: UUID,
    val entryId: UUID,
    val seq: Long,
    val ts: String,
    val outcome: String,
    val advisory: Boolean,
    val correlationType: String,
    val correlationId: String,
    val modelId: String,
    val modelVersion: String,
    val verdict: Map<String, Any?>?,
    val retrievedContext: List<MdrRetrievedContextRefView>?,
    val errorDetail: String?,
    val rulingInForce: String?,
)

// Response for the replay read: GET /api/v1/admin/mdr/decisions/{decisionId}. The full Entry
// thread in chain order -- the one Model Decision followed by every Human Disposition that rules
// on it, oldest first. `rulingInForce` is the latest Disposition's kind (or absent when none),
// the same value the gate evaluates. Null fields are omitted (non_null), so each view carries
// only what its Outcome / ruling populated.
data class MdrDecisionThreadResponse(
    val decisionId: UUID,
    val correlationType: String,
    val correlationId: String,
    val modelDecision: MdrModelDecisionView,
    val dispositions: List<MdrDispositionView>,
    val rulingInForce: String?,
)

data class MdrModelDecisionView(
    val entryId: UUID,
    val seq: Long,
    val ts: String,
    val outcome: String,
    val modelId: String,
    val modelVersion: String,
    val advisory: Boolean,
    val promptTemplateId: String,
    val promptTemplateVersion: String,
    val promptTemplateHash: String,
    val renderedPrompt: String,
    val retrievedContext: List<MdrRetrievedContextRefView>?,
    val rawOutput: String?,
    val verdict: Map<String, Any?>?,
    val errorDetail: String?,
    val latencyMs: Long,
    val costMicros: Long,
    val costCurrency: String,
)

data class MdrRetrievedContextRefView(
    val type: String,
    val id: String,
    val contentHash: String,
)

// A Human Disposition as seen in the replay thread. For an EDIT ruling `editedOutput` carries the
// stored replacement and `diff` carries the line-level difference from the Model Decision's Raw
// output -- derived at read time from the two immutable inputs, never stored (so it can never drift
// from the artifacts it describes). ACCEPT / REJECT omit both.
data class MdrDispositionView(
    val entryId: UUID,
    val seq: Long,
    val ts: String,
    val disposition: String,
    val humanId: UUID,
    val parentEntryId: UUID,
    val editedOutput: String?,
    val diff: List<MdrDiffLine>?,
)

data class MdrDiffLine(
    val op: String,
    val text: String,
)
