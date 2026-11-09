package com.malai.auth.acceptance.audit

import tools.jackson.databind.ObjectMapper
import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.VerificationResult
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/**
 * Integration test for [com.malai.security.audit.AuditChainVerifier]. Drives the writer to
 * build a clean chain, then proves the verifier returns Ok for it AND detects each
 * STRIDE tampering case from Plan 02-11's threat register (T-02-C11-01..04).
 */
class AuditChainVerifierTest : AuthAcceptanceTest(), AuditTestSupport {

    private val verifier: AuditChainVerifier get() = getBean(AuditChainVerifier::class.java)
    private val jdbc: JdbcTemplate get() = getBean(JdbcTemplate::class.java)

    @Test
    fun `clean chain returns Ok with the row count`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "B")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "C")))

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.Ok>()
        result.rowsVerified shouldBe 3L
    }

    @Test
    fun `empty audit_events still returns Ok with zero rows when chain_head is at genesis`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.Ok>()
        result.rowsVerified shouldBe 0L
    }

    @Test
    fun `T-02-C11-01 tampered current_hash returns CurrentHashMismatch`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(theCurrentHashOfAuditEventAtSeqIsTamperedTo(seq = 1L, badHash = ByteArray(32) { 0xFF.toByte() }))

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
        result.seq shouldBe 1L
    }

    @Test
    fun `T-02-C11-02 deleted row returns GapAtSeq`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "B")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "C")))

        // Tamper: a row in the middle of the chain disappears. This requires
        // superuser rights in production -- malai_app has REVOKE DELETE per V11.
        // The test runs as the docker-compose superuser so we can simulate the
        // out-of-band attacker scenario the verifier exists to catch.
        jdbc.update("DELETE FROM audit_events WHERE seq = 2")

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.GapAtSeq>()
        result.expected shouldBe 2L
        result.actual shouldBe 3L
    }

    @Test
    fun `streams rows in batches across multiple pages and returns Ok for a chain larger than batch size`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(nAuditEventsAreRecorded(7))

        // Same JdbcTemplate + ObjectMapper as the production bean, but a tiny
        // batchSize so the 7-row chain forces 4 paged queries (2+2+2+1).
        val pagedVerifier = AuditChainVerifier(
            jdbc = getBean(JdbcTemplate::class.java),
            objectMapper = getBean(ObjectMapper::class.java),
            batchSize = 2,
        )

        val result = pagedVerifier.verify()

        result.shouldBeInstanceOf<VerificationResult.Ok>()
        result.rowsVerified shouldBe 7L
    }

    @Test
    fun `verify returns Ok when state map contains a BigDecimal (writer-verifier number coercion)`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        // Money is the bank's core data and the natural reflex is to put a
        // BigDecimal in afterState. Before review #8 the writer hashed
        // BigDecimal("1.50") as "1.50" but the verifier re-parsed JSONB '1.50'
        // as Double 1.5 (toString "1.5") -- hash mismatch on a clean chain.
        // The writer now round-trips through Jackson before hashing, so both
        // sides agree on the JSONB-serialised numeric form.
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = UUID.randomUUID(),
            actorType = ActorType.SYSTEM,
            action = "TRANSFER_PROBE",
            afterState = mapOf(
                "amount" to java.math.BigDecimal("1.50"),
                "fee" to java.math.BigDecimal("0.01"),
                "amountMinorUnits" to 150L,
            ),
        )))

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.Ok>()
        result.rowsVerified shouldBe 1L
    }

    @Test
    fun `T-02-C11-03 mutated afterState JSONB without recomputed hash returns CurrentHashMismatch`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(
            actorId = UUID.randomUUID(),
            actorType = ActorType.CUSTOMER,
            action = "TRANSFER_EXECUTED",
            afterState = mapOf("amount" to 100, "memo" to "rent"),
        )))

        // Tamper: an attacker with DB write rights edits the afterState payload
        // (e.g. retroactively rewrites the transfer amount to a smaller number)
        // but cannot recompute the SHA-256 chain because they don't see prev_hash
        // or the writer's canonicalisation logic. The verifier recomputes from
        // the on-disk row + prev_hash and detects the divergence.
        // Same recomputation path catches actor_id / action / resource_id /
        // beforeState swaps -- testing one shape is sufficient evidence the
        // recompute is wired.
        jdbc.update(
            "UPDATE audit_events SET after_state = ?::jsonb WHERE seq = 1",
            "{\"amount\":1,\"memo\":\"rent\"}",
        )

        val result = verifier.verify()

        result.shouldBeInstanceOf<VerificationResult.CurrentHashMismatch>()
        result.seq shouldBe 1L
    }

    @Test
    fun `T-02-C11-04 chain_head divergence returns HeadMismatch`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "A")))
        and(anAuditEventIsRecorded(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "B")))

        // Tamper: chain_head advanced beyond the actual rows -- as if an attacker
        // added a phantom seq entry to the head pointer to mask a missing row.
        jdbc.update(
            "UPDATE chain_head SET last_seq = ?, last_hash = ? WHERE scope = 'global'",
            99L, ByteArray(32) { 0xAA.toByte() },
        )

        val result = verifier.verify()

        result shouldBe VerificationResult.HeadMismatch
    }
}
