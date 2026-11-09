package com.malai.mdr.chain

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import java.sql.Timestamp
import java.util.UUID

// Reconstructs the hash-covered [MdrEntryFields] projection from a persisted mdr_entries row.
// The verifier walks stored rows and must re-derive the SAME projection the writer hashed, so
// this single mapper is the one place a row becomes fields -- reused by [MdrChainVerifier] in
// production and by the in-test hash recompute, exactly as the audit plane reconstructs an
// AuditEvent from its row. The JSONB columns (retrieved_context, verdict) arrive as text
// (SELECT ... ::text) and are parsed back to the Map/List form CanonicalJson sorts; the
// prompt-template hash arrives as raw bytes and is rendered hex.
object MdrEntryRowMapper {

    fun toFields(row: Map<String, Any?>, objectMapper: ObjectMapper): MdrEntryFields =
        MdrEntryFields(
            entryKind = row["entry_kind"] as String,
            ts = (row["ts"] as Timestamp).toInstant(),
            decisionId = row["decision_id"] as UUID,
            parentEntryId = row["parent_entry_id"] as UUID?,
            correlationType = row["correlation_type"] as String,
            correlationId = row["correlation_id"] as String,
            advisory = row["advisory"] as Boolean?,
            modelId = row["model_id"] as String?,
            modelVersion = row["model_version"] as String?,
            promptTemplateId = row["prompt_template_id"] as String?,
            promptTemplateVersion = row["prompt_template_version"] as String?,
            promptTemplateHashHex = (row["prompt_template_hash"] as ByteArray?)?.let(::toHex),
            renderedPrompt = row["rendered_prompt"] as String?,
            retrievedContext = (row["retrieved_context"] as String?)?.let { objectMapper.readValue(it, LIST_TYPE) },
            outcome = row["outcome"] as String?,
            rawOutput = row["raw_output"] as String?,
            verdict = (row["verdict"] as String?)?.let { objectMapper.readValue(it, MAP_TYPE) },
            errorDetail = row["error_detail"] as String?,
            latencyMs = (row["latency_ms"] as Number?)?.toLong(),
            costMicros = (row["cost_micros"] as Number?)?.toLong(),
            costCurrency = row["cost_currency"] as String?,
            humanId = row["human_id"] as UUID?,
            disposition = row["disposition"] as String?,
            editedOutput = row["edited_output"] as String?,
        )

    fun toHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private val MAP_TYPE = object : TypeReference<Map<String, Any?>>() {}
    private val LIST_TYPE = object : TypeReference<List<Map<String, Any?>>>() {}
}
