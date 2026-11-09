package com.malai.security.audit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuditRedactorTest {

    @Test
    fun `redact returns null for null input`() {
        assertThat(AuditRedactor.redact(null)).isNull()
    }

    @Test
    fun `redact returns the same shape for a map with no sensitive keys`() {
        val input = mapOf("foo" to 1, "bar" to "two", "baz" to listOf(1, 2, 3))

        assertThat(AuditRedactor.redact(input)).isEqualTo(input)
    }

    @Test
    fun `redacts a top-level password value`() {
        val out = AuditRedactor.redact(mapOf("email" to "alice@example.com", "password" to "hunter2"))

        assertThat(out).isEqualTo(mapOf("email" to "alice@example.com", "password" to "[REDACTED]"))
    }

    @Test
    fun `redacts case-insensitively and across naming conventions`() {
        val out = AuditRedactor.redact(mapOf(
            "PASSWORD" to "x",
            "Password_Hash" to "y",
            "user-password" to "z",
            "userPassword" to "w",
        ))

        assertThat(out!!.values).containsOnly("[REDACTED]")
    }

    @Test
    fun `redacts financial card fields`() {
        val out = AuditRedactor.redact(mapOf(
            "pan" to "4111111111111111",
            "cardNumber" to "5500000000000004",
            "cvv" to "123",
            "cvc" to "456",
            "pin" to "1234",
        ))

        assertThat(out!!.values).containsOnly("[REDACTED]")
    }

    @Test
    fun `redacts auth tokens`() {
        val out = AuditRedactor.redact(mapOf(
            "accessToken" to "a",
            "refreshToken" to "b",
            "idToken" to "c",
            "bearer_token" to "d",
            "sessionToken" to "e",
            "apiKey" to "f",
            "secret" to "g",
            "privateKey" to "h",
        ))

        assertThat(out!!.values).containsOnly("[REDACTED]")
    }

    @Test
    fun `redacts within nested maps recursively`() {
        val out = AuditRedactor.redact(mapOf(
            "user" to mapOf(
                "email" to "alice@example.com",
                "credentials" to mapOf("password" to "hunter2", "salt" to "abc"),
            ),
        ))

        @Suppress("UNCHECKED_CAST")
        val user = out!!["user"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val creds = user["credentials"] as Map<String, Any?>
        assertThat(user["email"]).isEqualTo("alice@example.com")
        assertThat(creds["password"]).isEqualTo("[REDACTED]")
        assertThat(creds["salt"]).isEqualTo("abc")
    }

    @Test
    fun `redacts within lists of maps`() {
        val out = AuditRedactor.redact(mapOf(
            "attempts" to listOf(
                mapOf("password" to "first", "ts" to 1),
                mapOf("password" to "second", "ts" to 2),
            ),
        ))

        @Suppress("UNCHECKED_CAST")
        val attempts = out!!["attempts"] as List<Map<String, Any?>>
        assertThat(attempts.map { it["password"] }).containsOnly("[REDACTED]")
        assertThat(attempts.map { it["ts"] }).containsExactly(1, 2)
    }

    @Test
    fun `redacts a malai pan appearing as a value under an innocuous key`() {
        val out = AuditRedactor.redact(mapOf("note" to "9904320000012341"))

        assertThat(out).isEqualTo(mapOf("note" to "[REDACTED]"))
    }

    @Test
    fun `redacts a malai pan embedded in free text`() {
        val out = AuditRedactor.redact(mapOf("message" to "authorised card 9904320000012341 for GBP 12.00"))

        assertThat(out!!["message"]).isEqualTo("authorised card [REDACTED] for GBP 12.00")
    }

    @Test
    fun `does not redact a pan-shaped digit run inside a hex digest`() {
        val input = mapOf(
            "contentHash" to "22a0a88b17b223ba073a22dc3ae2307db09f47e2f9fdc9800079125683371dca",
            "leadingRun" to "9800079125683371dca0a88b17b223ba073a22dc3ae2307db09f47e2f9fdc22a",
            "trailingRun" to "dca0a88b17b223ba073a22dc3ae2307db09f47e2f9fdc22a9800079125683371",
        )

        assertThat(AuditRedactor.redact(input)).isEqualTo(input)
    }

    @Test
    fun `redacts a malai pan set off by punctuation`() {
        val out = AuditRedactor.redact(mapOf("message" to "card=9904320000012341; ref:9904320000012341."))

        assertThat(out!!["message"]).isEqualTo("card=[REDACTED]; ref:[REDACTED].")
    }

    @Test
    fun `does not redact account numbers, sort codes, or short digit runs`() {
        val input = mapOf(
            "accountNumber" to "12345678",
            "sortCode" to "65-82-00",
            "amountMinorUnits" to "250000",
        )

        assertThat(AuditRedactor.redact(input)).isEqualTo(input)
    }

    @Test
    fun `does not redact unrelated fields that happen to share a prefix`() {
        val out = AuditRedactor.redact(mapOf(
            "panel" to "ok",
            "spincount" to 7,
        ))

        assertThat(out).isEqualTo(mapOf("panel" to "ok", "spincount" to 7))
    }

    @Test
    fun `preserves the original key spelling, only replaces the value`() {
        val out = AuditRedactor.redact(mapOf("Password" to "x"))

        assertThat(out!!.keys).containsExactly("Password")
    }
}
