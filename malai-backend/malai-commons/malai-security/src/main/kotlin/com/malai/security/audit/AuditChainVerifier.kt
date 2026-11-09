package com.malai.security.audit

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.ObjectMapper
import com.malai.security.chain.ChainHeads
import com.malai.security.chain.ChainScopeSource
import com.malai.security.chain.ChainVerifier
import com.malai.security.chain.ChainedRow
import com.malai.security.chain.ScopeChainVerifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.sql.Timestamp
import java.util.UUID

// VerificationResult is the return contract of AuditChainVerifier.verify() and
// of the shared [ChainVerifier] for every chain scope. All failure modes are
// first-class so the seeder --verify-chain CLI (Plan 02-15) can give a buyer
// the precise reason a chain is broken. Ok carries the row count so the same
// call doubles as a smoke check. The declaration stays in this package because
// Kotlin type aliases cannot resolve nested objects like HeadMismatch in
// expression position, which every existing consumer relies on.
sealed interface VerificationResult {
    data class Ok(val rowsVerified: Long) : VerificationResult
    data class GapAtSeq(val expected: Long, val actual: Long) : VerificationResult
    data class PrevHashMismatch(val seq: Long) : VerificationResult
    data class CurrentHashMismatch(val seq: Long) : VerificationResult
    data object HeadMismatch : VerificationResult
}

// Replay verifier (D-2.11). Delegates the batched seq-ordered walk (genesis 32
// zero bytes, prev_hash linkage, hash recompute, chain_head cross-check) to the
// shared [ChainVerifier] over scope 'global', supplying audit_events rows and
// their canonical payloads. Uses the SAME [CanonicalJson] serializer as
// [HashChainedAuditWriter] -- any divergence between the two breaks every
// previously-written row, hence the verifier guards the writer's hashing
// contract with a real chain.
//
// Memory (review #4): rows are read in seq-ordered batches of [batchSize] (default
// 1000) so the verifier never holds the whole table in memory regardless of
// how large audit_events grows.
//
// Snapshot consistency: chain_head is read FIRST and pins targetSeq for the
// run. The walk only verifies rows up to targetSeq, so concurrent appends
// during a verify() do not race the head cross-check.
//
// KNOWN LIMITATION -- TAMPER-DETECTING, NOT TAMPER-EVIDENT-TO-THIRD-PARTIES
// (review #6, deliberately not closed in v1):
//
// The chain detects a tamper that does NOT consistently rewrite every
// downstream hash. A DB superuser who can edit any row CAN re-hash the
// entire chain from genesis after a tamper -- verify() would then return Ok
// and the tamper would be undetectable from the inside.
//
// The mitigation is to periodically anchor chain_head.last_hash somewhere
// outside the bank's DB control plane:
//   * sign last_hash with KMS and write the signature + seq + timestamp to
//     S3 Object Lock (compliance retention) hourly,
//   * publish a Merkle-root-style attestation to a regulator-side notary, or
//   * append last_hash to an external WORM store on every verify run.
//
// This is a follow-up; v1 ships without it. Until then the verifier's claim
// is "we detect any tamper that did not also coordinate every downstream
// hash and chain_head", which is sufficient against the application-layer
// and curious-DBA threat models but NOT against a determined inside threat
// with full DB write access.
@Service
class AuditChainVerifier(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${audit.verify.batch-size:1000}") private val batchSize: Int,
) : ScopeChainVerifier {

    override val scope = "global"

    override fun verify(): VerificationResult = ChainVerifier(ChainHeads(jdbc), batchSize).verify(auditEvents)

    private val auditEvents = object : ChainScopeSource {
        override val scope = "global"

        override fun rowsBetween(seqExclusive: Long, seqInclusiveMax: Long, limit: Int): List<ChainedRow> =
            jdbc.queryForList(
                """
                SELECT seq, prev_hash, current_hash, event_id, ts, actor_id, actor_type, action,
                       resource_type, resource_id,
                       before_state::text AS before_state, after_state::text AS after_state,
                       ip::text AS ip, user_agent, request_id, trace_id
                FROM audit_events
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
                    canonicalPayload = CanonicalJson.serialize(rowToEvent(row)),
                )
            }
    }

    private fun rowToEvent(row: Map<String, Any?>): AuditEvent =
        AuditEvent(
            actorId = (row["actor_id"] as? UUID)
                ?: row["actor_id"]?.toString()?.let(UUID::fromString),
            actorType = ActorType.valueOf(row["actor_type"] as String),
            action = row["action"] as String,
            resourceType = row["resource_type"] as String?,
            resourceId = row["resource_id"] as String?,
            beforeState = (row["before_state"] as String?)?.let(::parseJsonMap),
            afterState = (row["after_state"] as String?)?.let(::parseJsonMap),
            ip = (row["ip"] as String?)?.let(InetAddress::getByName),
            userAgent = row["user_agent"] as String?,
            requestId = row["request_id"] as String?,
            traceId = row["trace_id"] as String?,
            ts = (row["ts"] as Timestamp).toInstant(),
        )

    private fun parseJsonMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})
}
