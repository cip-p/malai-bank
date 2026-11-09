package com.malai.auth.acceptance.audit

import com.malai.auth.acceptance.AuthAcceptanceTest
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import com.malai.security.audit.CanonicalJson
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Integration test for [com.malai.security.audit.HashChainedAuditWriter] (D-2.11, BACK-05).
 * Piggybacks AuthAcceptanceTest's running Spring context + the docker-compose Postgres
 * so the writer exercises the real chain_head FOR UPDATE lock + audit_events INSERT path.
 */
class HashChainedAuditWriterTest : AuthAcceptanceTest(), AuditTestSupport {

    private val writer: AuditWriter get() = getBean(AuditWriter::class.java)

    @Test
    fun `first record advances the chain from genesis to seq 1`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        and(anAuditEventIsRecorded(
            AuditEvent(
                actorId = UUID.fromString("11111111-1111-1111-1111-111111111111"),
                actorType = ActorType.CUSTOMER,
                action = "LOGIN_SUCCEEDED",
                traceId = "trace-1",
            )
        ))

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            (this[0]["seq"] as Number).toLong() shouldBe 1L
            this[0]["action"] shouldBe "LOGIN_SUCCEEDED"
            this[0]["actor_type"] shouldBe "CUSTOMER"
            (this[0]["prev_hash"] as ByteArray) shouldBe ByteArray(32)
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }
    }

    @Test
    fun `consecutive records advance seq monotonically`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        and(anAuditEventIsRecorded(
            AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.CUSTOMER, action = "A")
        ))
        and(anAuditEventIsRecorded(
            AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.CUSTOMER, action = "B")
        ))
        and(anAuditEventIsRecorded(
            AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.CUSTOMER, action = "C")
        ))

        and(theAuditEventsInTheDatabase()) {
            this.map { (it["seq"] as Number).toLong() } shouldBe listOf(1L, 2L, 3L)
            this.map { it["action"] } shouldBe listOf("A", "B", "C")
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 3L
        }
    }

    @Test
    fun `each row's current_hash equals SHA-256 of prev_hash plus canonical JSON of the event`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        // Pin everything that the writer would otherwise stamp from MDC / Clock so we can
        // recompute the expected hash from the known event payload below.
        val event = AuditEvent(
            actorId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
            actorType = ActorType.SYSTEM,
            action = "SEED_INIT",
            ts = Instant.parse("2026-05-06T12:00:00Z"),
            traceId = "fixed-trace",
        )
        and(anAuditEventIsRecorded(event))

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            val row = this[0]
            val expectedHash = MessageDigest.getInstance("SHA-256")
                .digest((row["prev_hash"] as ByteArray) + CanonicalJson.serialize(event).toByteArray(Charsets.UTF_8))
            (row["current_hash"] as ByteArray) shouldBe expectedHash
        }
    }

    @Test
    fun `record rolls back with the caller's transaction (atomic-with-business-write contract)`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        val txTemplate = TransactionTemplate(getBean(PlatformTransactionManager::class.java))
        runCatching {
            txTemplate.execute<Unit> {
                writer.record(AuditEvent(
                    actorId = UUID.randomUUID(),
                    actorType = ActorType.CUSTOMER,
                    action = "TRANSFER_EXECUTED",
                ))
                throw IllegalStateException("simulated business-write failure after audit")
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this.shouldBeEmpty()
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 0L
        }
    }

    @Test
    fun `recordIndependently survives the caller's transaction rolling back (failure-path persistence)`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        val txTemplate = TransactionTemplate(getBean(PlatformTransactionManager::class.java))
        runCatching {
            txTemplate.execute<Unit> {
                writer.recordIndependently(AuditEvent(
                    actorId = null,
                    actorType = ActorType.SYSTEM,
                    action = "LOGIN_FAILED",
                    afterState = mapOf("attemptedEmail" to "attacker@example.com"),
                ))
                throw IllegalStateException("simulated business-write failure after audit")
            }
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "LOGIN_FAILED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["actor_id"] shouldBe null
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }
    }

    @Test
    fun `redacts sensitive keys in afterState before persisting and hashing`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        and(anAuditEventIsRecorded(AuditEvent(
            actorId = UUID.randomUUID(),
            actorType = ActorType.CUSTOMER,
            action = "AUDIT_REDACTION_PROBE",
            afterState = mapOf(
                "email" to "alice@example.com",
                "password" to "hunter2",
                "cardNumber" to "4111111111111111",
                "nested" to mapOf("apiKey" to "secret-key", "label" to "ok"),
            ),
        )))

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            val afterStateJson = this[0]["after_state"] as String
            afterStateJson shouldContain "\"email\""
            afterStateJson shouldContain "alice@example.com"
            afterStateJson shouldContain "[REDACTED]"
            afterStateJson shouldNotContain "hunter2"
            afterStateJson shouldNotContain "4111111111111111"
            afterStateJson shouldNotContain "secret-key"
            afterStateJson shouldContain "\"label\""
            afterStateJson shouldContain "\"ok\""
        }
    }

    @Test
    fun `chain_head last_hash matches the most recent row's current_hash`() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())

        and(anAuditEventIsRecorded(
            AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "X")
        ))
        and(anAuditEventIsRecorded(
            AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "Y")
        ))

        // Capture both reads via the DSL, then cross-compare in a single block.
        var lastRowHash: ByteArray? = null
        var lastRowSeq: Long = -1
        and(theAuditEventsInTheDatabase()) {
            val lastRow = this.last()
            lastRowHash = lastRow["current_hash"] as ByteArray
            lastRowSeq = (lastRow["seq"] as Number).toLong()
        }
        and(theChainHeadInTheDatabase()) {
            (this["last_hash"] as ByteArray) shouldBe lastRowHash
            (this["last_seq"] as Number).toLong() shouldBe lastRowSeq
        }
    }
}
