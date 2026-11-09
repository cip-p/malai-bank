package com.malai.auth.acceptance.audit

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.VerificationResult
import com.malai.security.chain.ChainVerificationJob
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Integration test for [com.malai.security.chain.ChainVerificationJob]. The verifier's own
 * state-transition coverage (Ok / GapAtSeq / PrevHashMismatch / CurrentHashMismatch /
 * HeadMismatch) lives in [AuditChainVerifierTest]; this class only proves the job wires every
 * [com.malai.security.chain.ScopeChainVerifier] present and exposes each scope's result, and that
 * @SchedulerLock blocks concurrent invocations across horizontally-scaled instances. The auth
 * service context carries only the 'global' scope verifier (the MDR verifier is another module).
 */
class ChainVerificationJobTest : AuthAcceptanceTest(), AuditTestSupport {

    private val job: ChainVerificationJob get() = getBean(ChainVerificationJob::class.java)

    @Test
    fun `runVerification verifies every scope and exposes each result on lastResults`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theShedLockIsReleased(ChainVerificationJob.LOCK_NAME))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))

        job.runVerification()

        val globalResult = job.lastResultForScope("global")
        globalResult.shouldBeInstanceOf<VerificationResult.Ok>()
        globalResult.rowsVerified shouldBe 1L
        (job.lastRunAt != null) shouldBe true
    }

    @Test
    fun `runVerification is skipped when another instance holds the ShedLock`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(theShedLockIsReleased(ChainVerificationJob.LOCK_NAME))
        and(theShedLockIsHeldByAnotherInstance(ChainVerificationJob.LOCK_NAME))

        val lastRunAtBefore = job.lastRunAt
        job.runVerification()
        val lastRunAtAfter = job.lastRunAt

        lastRunAtAfter shouldBe lastRunAtBefore

        and(theShedLockIsReleased(ChainVerificationJob.LOCK_NAME))
    }
}
