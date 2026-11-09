package com.malai.mdr.chain

import java.time.Instant
import java.util.UUID
import com.malai.security.chain.CanonicalJson as ChainCanonicalJson

// The hash-covered projection of an MDR entry. Every semantic column is present so a single
// projection serves both entry kinds (Model Decision and, later, Human Disposition) and every
// outcome -- null fields are emitted, never omitted, so a future kind cannot silently change a
// historical hash. Excluded (same rationale as the audit chain): entry_id, seq, prev_hash --
// writer/DB-assigned identity is out, content is in.
data class MdrEntryFields(
    val entryKind: String,
    val ts: Instant,
    val decisionId: UUID,
    val parentEntryId: UUID?,
    val correlationType: String,
    val correlationId: String,
    val advisory: Boolean?,
    val modelId: String?,
    val modelVersion: String?,
    val promptTemplateId: String?,
    val promptTemplateVersion: String?,
    val promptTemplateHashHex: String?,
    val renderedPrompt: String?,
    val retrievedContext: List<Map<String, Any?>>?,
    val outcome: String?,
    val rawOutput: String?,
    val verdict: Map<String, Any?>?,
    val errorDetail: String?,
    val latencyMs: Long?,
    val costMicros: Long?,
    val costCurrency: String?,
    val humanId: UUID?,
    val disposition: String?,
    val editedOutput: String?,
)

// Deterministic canonical JSON for the MDR chain's hash payload. The emission rules (sorted
// keys, escape set, null handling, type formatting) live in the shared chain core
// [com.malai.security.chain.CanonicalJson]; this object owns only the fixed projection of an
// MDR entry. Writer and verifier both call serialize(fields), so the two sides cannot diverge --
// exactly as [com.malai.security.audit.CanonicalJson] does for the audit chain.
object MdrEntryCanonicalJson {

    fun serialize(fields: MdrEntryFields): String =
        ChainCanonicalJson.serialize(
            mapOf(
                "advisory" to fields.advisory,
                "correlationId" to fields.correlationId,
                "correlationType" to fields.correlationType,
                "costCurrency" to fields.costCurrency,
                "costMicros" to fields.costMicros,
                "decisionId" to fields.decisionId.toString(),
                "disposition" to fields.disposition,
                "editedOutput" to fields.editedOutput,
                "entryKind" to fields.entryKind,
                "errorDetail" to fields.errorDetail,
                "humanId" to fields.humanId?.toString(),
                "latencyMs" to fields.latencyMs,
                "modelId" to fields.modelId,
                "modelVersion" to fields.modelVersion,
                "outcome" to fields.outcome,
                "parentEntryId" to fields.parentEntryId?.toString(),
                "promptTemplateHash" to fields.promptTemplateHashHex,
                "promptTemplateId" to fields.promptTemplateId,
                "promptTemplateVersion" to fields.promptTemplateVersion,
                "rawOutput" to fields.rawOutput,
                "renderedPrompt" to fields.renderedPrompt,
                "retrievedContext" to fields.retrievedContext,
                "ts" to fields.ts.toString(),
                "verdict" to fields.verdict,
            ),
        )
}
