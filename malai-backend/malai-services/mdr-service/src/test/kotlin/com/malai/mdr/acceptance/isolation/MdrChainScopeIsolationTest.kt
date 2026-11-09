package com.malai.mdr.acceptance.isolation

import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.mdr.service.MdrChainVerifier
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.VerificationResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.util.UUID

class MdrChainScopeIsolationTest : MdrAcceptanceTest() {

    private val mdrVerifier: MdrChainVerifier get() = getBean(MdrChainVerifier::class.java)
    private val auditVerifier: AuditChainVerifier get() = getBean(AuditChainVerifier::class.java)

    private val someEntryId = UUID.fromString("1a000000-0000-0000-0000-000000000001")
    private val someDecisionId = UUID.fromString("1d000000-0000-0000-0000-000000000001")

    @Test
    fun tamperingTheMdrChainFailsOnlyTheMdrVerificationLeavingAuditOk() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anMdrModelDecisionIsRecorded(someEntryId, someDecisionId))
        and(theCurrentHashOfMdrEntryAtSeqIsTamperedTo(seq = 1L, badHash = ByteArray(32) { 0xFF.toByte() }))

        val mdrResult = mdrVerifier.verify()
        mdrResult.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
        mdrResult.seq shouldBe 1L

        val auditResult = auditVerifier.verify()
        auditResult.shouldBeInstanceOf<VerificationResult.Ok>()
        auditResult.rowsVerified shouldBe 1L
    }

    @Test
    fun tamperingTheAuditChainFailsOnlyTheAuditVerificationLeavingMdrOk() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        and(theAuditChainIsAtGenesis())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anMdrModelDecisionIsRecorded(someEntryId, someDecisionId))
        and(theCurrentHashOfAuditEventAtSeqIsTamperedTo(seq = 1L, badHash = ByteArray(32) { 0xFF.toByte() }))

        val auditResult = auditVerifier.verify()
        auditResult.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
        auditResult.seq shouldBe 1L

        val mdrResult = mdrVerifier.verify()
        mdrResult.shouldBeInstanceOf<VerificationResult.Ok>()
        mdrResult.rowsVerified shouldBe 1L
    }
}
