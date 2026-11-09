package com.malai.auth.acceptance.audit

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.VerificationResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class ChainScopeIsolationTest : AuthAcceptanceTest(), ChainScopeIsolationTestSupport {

    private val auditVerifier: AuditChainVerifier get() = getBean(AuditChainVerifier::class.java)

    @Test
    fun `appending to a second scope advances that scope's head without moving the audit chain head`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theProbeChainScopeIsAtGenesis())

        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "AUDIT-ONLY")))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-1"))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-2"))

        and(theProbeChainEntriesInTheDatabase()) {
            this shouldHaveSize 2
            this.map { (it["seq"] as Number).toLong() } shouldBe listOf(1L, 2L)
            (this[0]["prev_hash"] as ByteArray) shouldBe ByteArray(32)
            (this[1]["prev_hash"] as ByteArray) shouldBe (this[0]["current_hash"] as ByteArray)
        }
        and(theProbeChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 2L
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }
    }

    @Test
    fun `each scope verifies independently with its own row count`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theProbeChainScopeIsAtGenesis())

        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "B")))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-1"))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-2"))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-3"))

        and(theProbeChainVerificationResult(batchSize = 2)) {
            this.shouldBeInstanceOf<VerificationResult.Ok>()
            this.rowsVerified shouldBe 3L
        }

        val auditResult = auditVerifier.verify()
        auditResult.shouldBeInstanceOf<VerificationResult.Ok>()
        auditResult.rowsVerified shouldBe 2L
    }

    @Test
    fun `tampering the probe scope fails only the probe scope's verification`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theProbeChainScopeIsAtGenesis())

        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-1"))
        and(theCurrentHashOfProbeEntryAtSeqIsTamperedTo(seq = 1L, badHash = ByteArray(32) { 0xFF.toByte() }))

        and(theProbeChainVerificationResult()) {
            this.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
            this.seq shouldBe 1L
        }

        val auditResult = auditVerifier.verify()
        auditResult.shouldBeInstanceOf<VerificationResult.Ok>()
        auditResult.rowsVerified shouldBe 1L
    }

    @Test
    fun `tampering the audit chain fails only the audit chain's verification`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theProbeChainScopeIsAtGenesis())

        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anEntryIsAppendedToTheProbeScope("probe-entry-1"))
        and(theCurrentHashOfAuditEventAtSeqIsTamperedTo(seq = 1L, badHash = ByteArray(32) { 0xFF.toByte() }))

        val auditResult = auditVerifier.verify()
        auditResult.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
        auditResult.seq shouldBe 1L

        and(theProbeChainVerificationResult()) {
            this.shouldBeInstanceOf<VerificationResult.Ok>()
            this.rowsVerified shouldBe 1L
        }
    }
}
