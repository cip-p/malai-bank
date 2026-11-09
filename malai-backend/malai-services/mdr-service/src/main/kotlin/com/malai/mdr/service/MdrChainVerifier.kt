package com.malai.mdr.service

import tools.jackson.databind.ObjectMapper
import com.malai.mdr.chain.MdrEntryCanonicalJson
import com.malai.mdr.chain.MdrEntryRowMapper
import com.malai.security.audit.VerificationResult
import com.malai.security.chain.ChainHeads
import com.malai.security.chain.ChainScopeSource
import com.malai.security.chain.ChainVerifier
import com.malai.security.chain.ChainedRow
import com.malai.security.chain.ScopeChainVerifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service

// Replay verifier for the MDR chain (scope 'mdr'), the second [ScopeChainVerifier] tenant beside
// the audit plane's 'global' scope. Delegates the batched seq-ordered walk (genesis 32 zero bytes,
// prev_hash linkage, hash recompute, chain_head cross-check) to the shared [ChainVerifier],
// supplying mdr_entries rows and their canonical payloads. Uses the SAME [MdrEntryCanonicalJson]
// projection and [MdrEntryRowMapper] the writer hashed with -- any divergence between writer and
// verifier would break every previously-written row, so the verifier guards the writer's hashing
// contract with a real chain. The same tamper-detecting (not tamper-evident-to-third-parties)
// limitation documented on [com.malai.security.audit.AuditChainVerifier] applies here.
@Service
class MdrChainVerifier(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${mdr.verify.batch-size:1000}") private val batchSize: Int,
) : ScopeChainVerifier {

    override val scope = "mdr"

    override fun verify(): VerificationResult = ChainVerifier(ChainHeads(jdbc), batchSize).verify(mdrEntries)

    private val mdrEntries = object : ChainScopeSource {
        override val scope = "mdr"

        override fun rowsBetween(seqExclusive: Long, seqInclusiveMax: Long, limit: Int): List<ChainedRow> =
            jdbc.queryForList(
                """
                SELECT seq, prev_hash, current_hash, entry_id, ts, entry_kind, decision_id, parent_entry_id,
                       correlation_type, correlation_id, advisory, model_id, model_version, prompt_template_id,
                       prompt_template_version, prompt_template_hash, rendered_prompt,
                       retrieved_context::text AS retrieved_context, outcome, raw_output,
                       verdict::text AS verdict, error_detail, latency_ms, cost_micros, cost_currency,
                       human_id, disposition, edited_output
                FROM mdr_entries
                WHERE seq > ? AND seq <= ?
                ORDER BY seq ASC
                LIMIT ?
                """.trimIndent(),
                seqExclusive, seqInclusiveMax, limit.toLong(),
            ).map { row ->
                ChainedRow(
                    seq = (row["seq"] as Number).toLong(),
                    prevHash = row["prev_hash"] as ByteArray,
                    currentHash = row["current_hash"] as ByteArray,
                    canonicalPayload = MdrEntryCanonicalJson.serialize(MdrEntryRowMapper.toFields(row, objectMapper)),
                )
            }
    }
}
