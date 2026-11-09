package com.malai.security.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetAddress
import java.time.Instant
import java.util.UUID

class CanonicalJsonTest {

    private val anActorId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val aTimestamp = Instant.parse("2026-05-06T10:00:00Z")

    @Test
    fun `serialises the same event to identical bytes across calls`() {
        val event = AuditEvent(
            actorId = anActorId,
            actorType = ActorType.CUSTOMER,
            action = "LOGIN_SUCCEEDED",
            ts = aTimestamp,
            traceId = "abc123",
        )

        assertThat(CanonicalJson.serialize(event)).isEqualTo(CanonicalJson.serialize(event))
    }

    @Test
    fun `top-level keys appear in alphabetical order`() {
        val event = AuditEvent(
            actorId = anActorId,
            actorType = ActorType.CUSTOMER,
            action = "X",
        )
        val s = CanonicalJson.serialize(event)

        assertThat(s.indexOf("\"action\"")).isLessThan(s.indexOf("\"actorId\""))
        assertThat(s.indexOf("\"actorId\"")).isLessThan(s.indexOf("\"actorType\""))
        assertThat(s.indexOf("\"actorType\"")).isLessThan(s.indexOf("\"afterState\""))
    }

    @Test
    fun `nested map keys are sorted recursively`() {
        val event = AuditEvent(
            actorId = null,
            actorType = ActorType.SYSTEM,
            action = "X",
            afterState = mapOf("z" to 1, "a" to 2, "m" to 3),
        )
        val s = CanonicalJson.serialize(event)

        // Locate the afterState object only and assert its inner key ordering.
        val nested = s.substringAfter("\"afterState\":{").substringBefore("}")
        assertThat(nested.indexOf("\"a\"")).isLessThan(nested.indexOf("\"m\""))
        assertThat(nested.indexOf("\"m\"")).isLessThan(nested.indexOf("\"z\""))
    }

    @Test
    fun `null fields are emitted as JSON null literals (consistent presence)`() {
        val event = AuditEvent(
            actorId = null,
            actorType = ActorType.SYSTEM,
            action = "X",
        )
        val s = CanonicalJson.serialize(event)

        assertThat(s).contains("\"actorId\":null")
        assertThat(s).contains("\"resourceType\":null")
        assertThat(s).contains("\"traceId\":null")
        assertThat(s).contains("\"ts\":null")
    }

    @Test
    fun `output contains no whitespace characters`() {
        val event = AuditEvent(
            actorId = anActorId,
            actorType = ActorType.CUSTOMER,
            action = "TRANSFER_EXECUTED",
            afterState = mapOf("amount" to 100, "memo" to "rent"),
            ts = aTimestamp,
        )

        val s = CanonicalJson.serialize(event)

        assertThat(s).doesNotContain(" ").doesNotContain("\n").doesNotContain("\t")
    }

    @Test
    fun `UUID InetAddress and Instant are serialised via toString surrounded by quotes`() {
        val event = AuditEvent(
            actorId = anActorId,
            actorType = ActorType.CUSTOMER,
            action = "X",
            ip = InetAddress.getByName("198.51.100.7"),
            ts = aTimestamp,
        )
        val s = CanonicalJson.serialize(event)

        assertThat(s).contains("\"actorId\":\"11111111-1111-1111-1111-111111111111\"")
        assertThat(s).contains("\"ip\":\"198.51.100.7\"")
        assertThat(s).contains("\"ts\":\"2026-05-06T10:00:00Z\"")
    }

    @Test
    fun `string values are JSON-escaped`() {
        val event = AuditEvent(
            actorId = null,
            actorType = ActorType.SYSTEM,
            action = "X",
            afterState = mapOf("note" to "line1\nline2\twith \"quote\""),
        )
        val s = CanonicalJson.serialize(event)

        // The emitted escapes are: \n -> backslash-n, \t -> backslash-t, " -> backslash-".
        // Use a normal string with escapes so we encode the LITERAL backslash bytes.
        assertThat(s).contains("\"note\":\"line1\\nline2\\twith \\\"quote\\\"\"")
    }

    @Test
    fun `array order is preserved`() {
        val event = AuditEvent(
            actorId = null,
            actorType = ActorType.SYSTEM,
            action = "X",
            afterState = mapOf("steps" to listOf("first", "second", "third")),
        )
        val s = CanonicalJson.serialize(event)

        assertThat(s.indexOf("first")).isLessThan(s.indexOf("second"))
        assertThat(s.indexOf("second")).isLessThan(s.indexOf("third"))
    }

    @Test
    fun `differing field values produce different output`() {
        val a = AuditEvent(actorId = anActorId, actorType = ActorType.CUSTOMER, action = "A")
        val b = AuditEvent(actorId = anActorId, actorType = ActorType.CUSTOMER, action = "B")

        assertThat(CanonicalJson.serialize(a)).isNotEqualTo(CanonicalJson.serialize(b))
    }

    @Test
    fun `golden snapshot - fully populated event serialises to a fixed byte string (review #10)`() {
        // Locks the canonical output for a known event. Any change to CanonicalJson
        // (key order, escape rules, null handling, type-specific formatting, ...)
        // will break this test deterministically with a byte-for-byte diff -- much
        // louder than the chain hashes silently failing on rows written before the
        // refactor. If a future schema evolution adds a field to AuditEvent, this
        // assertion MUST be updated alongside the writer + verifier together.
        val event = AuditEvent(
            actorId = UUID.fromString("00000000-1111-2222-3333-444444444444"),
            actorType = ActorType.CUSTOMER,
            action = "TRANSFER_EXECUTED",
            resourceType = "Account",
            resourceId = "acc-99",
            beforeState = mapOf(
                "balance" to 1000,
                "status" to "ACTIVE",
            ),
            afterState = mapOf(
                "balance" to 850,
                "items" to listOf("first", "second"),
                "note" to "with \"quote\"",
            ),
            ip = InetAddress.getByName("198.51.100.7"),
            userAgent = "test-agent/1.0",
            requestId = "req-1",
            traceId = "trace-1",
            ts = Instant.parse("2026-05-08T12:00:00Z"),
        )

        val expected = """{"action":"TRANSFER_EXECUTED","actorId":"00000000-1111-2222-3333-444444444444","actorType":"CUSTOMER","afterState":{"balance":850,"items":["first","second"],"note":"with \"quote\""},"beforeState":{"balance":1000,"status":"ACTIVE"},"ip":"198.51.100.7","requestId":"req-1","resourceId":"acc-99","resourceType":"Account","traceId":"trace-1","ts":"2026-05-08T12:00:00Z","userAgent":"test-agent/1.0"}"""

        assertThat(CanonicalJson.serialize(event)).isEqualTo(expected)
    }
}
