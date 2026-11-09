package com.malai.security.chain

import com.malai.security.audit.VerificationResult
import org.slf4j.LoggerFactory

class ChainedRow(val seq: Long, val prevHash: ByteArray, val currentHash: ByteArray, val canonicalPayload: String)

interface ChainScopeSource {
    val scope: String

    fun rowsBetween(seqExclusive: Long, seqInclusiveMax: Long, limit: Int): List<ChainedRow>
}

class ChainVerifier(private val chainHeads: ChainHeads, private val batchSize: Int) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun verify(source: ChainScopeSource): VerificationResult {
        val head = chainHeads.read(source.scope)

        var expectedPrev = ByteArray(32)
        var lastCurrentHash = ByteArray(32)
        var expectedSeq = 0L
        var rows = 0L

        while (expectedSeq < head.lastSeq) {
            val batch = source.rowsBetween(expectedSeq, head.lastSeq, batchSize)

            if (batch.isEmpty()) {
                return VerificationResult.HeadMismatch
            }

            for (row in batch) {
                expectedSeq += 1
                if (row.seq != expectedSeq) return VerificationResult.GapAtSeq(expectedSeq, row.seq)

                if (!row.prevHash.contentEquals(expectedPrev)) return VerificationResult.PrevHashMismatch(row.seq)

                val recomputed = ChainHasher.hash(row.prevHash, row.canonicalPayload)
                if (!row.currentHash.contentEquals(recomputed)) return VerificationResult.CurrentHashMismatch(row.seq)

                expectedPrev = row.currentHash
                lastCurrentHash = row.currentHash
                rows += 1
            }
        }

        if (!head.lastHash.contentEquals(lastCurrentHash) || head.lastSeq != expectedSeq) {
            return VerificationResult.HeadMismatch
        }

        log.debug("chain verified scope={} rows={} batchSize={}", source.scope, rows, batchSize)
        return VerificationResult.Ok(rows)
    }
}
