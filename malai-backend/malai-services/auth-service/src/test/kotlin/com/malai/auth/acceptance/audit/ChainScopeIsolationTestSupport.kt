package com.malai.auth.acceptance.audit

import com.malai.security.chain.CanonicalJson
import com.malai.security.chain.ChainAppender
import com.malai.security.chain.ChainHeads
import com.malai.security.chain.ChainScopeSource
import com.malai.security.chain.ChainVerifier
import com.malai.security.chain.ChainedRow
import com.malai.test.acceptance.WithApplicationContext
import com.malai.test.acceptance.kensa.CallableAction
import dev.kensa.StateCollector
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

interface ChainScopeIsolationTestSupport : WithApplicationContext {

    private fun jdbc(): JdbcTemplate = getBean(JdbcTemplate::class.java)

    fun theProbeChainScopeIsAtGenesis() = CallableAction {
        jdbc().execute(
            """
            CREATE TABLE IF NOT EXISTS chain_isolation_probe (
                seq          BIGINT PRIMARY KEY,
                prev_hash    BYTEA  NOT NULL,
                current_hash BYTEA  NOT NULL,
                payload      TEXT   NOT NULL
            )
            """.trimIndent(),
        )
        jdbc().update("DELETE FROM chain_isolation_probe")
        jdbc().update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES (?, E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
            PROBE_SCOPE,
        )
    }

    fun anEntryIsAppendedToTheProbeScope(payload: String) = CallableAction {
        val jdbc = jdbc()
        val appender = ChainAppender(ChainHeads(jdbc))
        TransactionTemplate(getBean(PlatformTransactionManager::class.java)).executeWithoutResult {
            appender.append(PROBE_SCOPE, { CanonicalJson.serialize(mapOf("payload" to payload)) }) { link ->
                jdbc.update(
                    "INSERT INTO chain_isolation_probe (seq, prev_hash, current_hash, payload) VALUES (?, ?, ?, ?)",
                    link.seq, link.prevHash, link.currentHash, payload,
                )
            }
        }
    }

    fun theProbeChainVerificationResult(batchSize: Int = 1000) = StateCollector { _ ->
        ChainVerifier(ChainHeads(jdbc()), batchSize).verify(probeSource())
    }

    private fun probeSource() = object : ChainScopeSource {
        override val scope = PROBE_SCOPE

        override fun rowsBetween(seqExclusive: Long, seqInclusiveMax: Long, limit: Int): List<ChainedRow> =
            jdbc().queryForList(
                """
                SELECT seq, prev_hash, current_hash, payload
                FROM chain_isolation_probe
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
                    canonicalPayload = CanonicalJson.serialize(mapOf("payload" to row["payload"])),
                )
            }
    }

    fun theCurrentHashOfProbeEntryAtSeqIsTamperedTo(seq: Long, badHash: ByteArray) = CallableAction {
        jdbc().update("UPDATE chain_isolation_probe SET current_hash = ? WHERE seq = ?", badHash, seq)
    }

    fun theProbeChainHeadInTheDatabase() = StateCollector { _ ->
        jdbc().queryForMap("SELECT scope, last_hash, last_seq FROM chain_head WHERE scope = ?", PROBE_SCOPE)
    }

    fun theProbeChainEntriesInTheDatabase() = StateCollector { _ ->
        jdbc().queryForList(
            "SELECT seq, prev_hash, current_hash, payload FROM chain_isolation_probe ORDER BY seq ASC",
        )
    }

    companion object {
        const val PROBE_SCOPE = "isolation-probe"
    }
}
