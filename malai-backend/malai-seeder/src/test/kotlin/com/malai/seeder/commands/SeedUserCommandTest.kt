package com.malai.seeder.commands

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.jacksonObjectMapper
import com.malai.registrationseeder.RegistrationSeederService
import com.malai.registrationseeder.SeedResult
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when` as whenever
import picocli.CommandLine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.UUID

/**
 * Pins the SeedResult → JSON projection so a future refactor of the `when` block
 * cannot silently change the shape downstream shells (`./demo-reset-local.sh
 * | jq -r .outcome`) depend on. Each test stubs the service, runs the picocli
 * command directly, and asserts the parsed JSON object structure on stdout.
 */
class SeedUserCommandTest {

    private val mapper: ObjectMapper = jacksonObjectMapper()
    private val service: RegistrationSeederService = mock(RegistrationSeederService::class.java)
    private val originalOut: PrintStream = System.out
    private lateinit var stdoutBuffer: ByteArrayOutputStream

    @BeforeEach
    fun captureStdout() {
        stdoutBuffer = ByteArrayOutputStream()
        System.setOut(PrintStream(stdoutBuffer, true, Charsets.UTF_8))
    }

    @AfterEach
    fun restoreStdout() {
        System.setOut(originalOut)
    }

    @Test
    fun `Seeded result is emitted as JSON with outcome=seeded`() {
        val userId = UUID.fromString("11111111-1111-4111-8111-111111111111")
        val accountId = UUID.fromString("22222222-2222-4222-8222-222222222222")
        whenever(service.seedFor("fresh@example.test"))
            .thenReturn(SeedResult.Seeded(userId, accountId, transactionCount = 25, openingBalanceMinor = 750_000L))

        val exitCode = CommandLine(SeedUserCommand(service, mapper)).execute("--email=fresh@example.test")

        assertThat(exitCode).isEqualTo(0)
        val json = readSingleJsonLine()
        assertThat(json["outcome"]).isEqualTo("seeded")
        assertThat(json["userId"]).isEqualTo(userId.toString())
        assertThat(json["accountId"]).isEqualTo(accountId.toString())
        assertThat(json["transactionCount"]).isEqualTo(25)
        assertThat(json["openingBalanceMinor"]).isEqualTo(750_000)
    }

    @Test
    fun `Noop result is emitted as JSON with outcome=noop and existing count`() {
        val userId = UUID.fromString("33333333-3333-4333-8333-333333333333")
        val accountId = UUID.fromString("44444444-4444-4444-8444-444444444444")
        whenever(service.seedFor("sarah@example.test"))
            .thenReturn(SeedResult.Noop(userId, accountId, existingTransactionCount = 31))

        val exitCode = CommandLine(SeedUserCommand(service, mapper)).execute("--email=sarah@example.test")

        assertThat(exitCode).isEqualTo(0)
        val json = readSingleJsonLine()
        assertThat(json["outcome"]).isEqualTo("noop")
        assertThat(json["userId"]).isEqualTo(userId.toString())
        assertThat(json["accountId"]).isEqualTo(accountId.toString())
        assertThat(json["existingTransactionCount"]).isEqualTo(31)
    }

    @Test
    fun `UserNotFound result is emitted as JSON with outcome=user_not_found and the queried email`() {
        whenever(service.seedFor("unknown@example.test"))
            .thenReturn(SeedResult.UserNotFound(email = "unknown@example.test"))

        val exitCode = CommandLine(SeedUserCommand(service, mapper)).execute("--email=unknown@example.test")

        assertThat(exitCode).isEqualTo(0)
        val json = readSingleJsonLine()
        assertThat(json["outcome"]).isEqualTo("user_not_found")
        assertThat(json["email"]).isEqualTo("unknown@example.test")
    }

    @Test
    fun `NoActiveCurrentAccount result is emitted as JSON with outcome=no_active_current_account`() {
        val userId = UUID.fromString("55555555-5555-4555-8555-555555555555")
        whenever(service.seedFor("no-account@example.test"))
            .thenReturn(SeedResult.NoActiveCurrentAccount(userId = userId))

        val exitCode = CommandLine(SeedUserCommand(service, mapper)).execute("--email=no-account@example.test")

        assertThat(exitCode).isEqualTo(0)
        val json = readSingleJsonLine()
        assertThat(json["outcome"]).isEqualTo("no_active_current_account")
        assertThat(json["userId"]).isEqualTo(userId.toString())
    }

    @Test
    fun `picocli rejects invocation when --email is missing`() {
        val errBuf = ByteArrayOutputStream()
        val originalErr = System.err
        System.setErr(PrintStream(errBuf, true, Charsets.UTF_8))
        try {
            val exitCode = CommandLine(SeedUserCommand(service, mapper)).execute()
            assertThat(exitCode).isNotEqualTo(0)
        } finally {
            System.setErr(originalErr)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun readSingleJsonLine(): Map<String, Any?> {
        val raw = stdoutBuffer.toString(Charsets.UTF_8)
        val jsonLines = raw.lineSequence().filter { it.startsWith("{") }.toList()
        assertThat(jsonLines)
            .withFailMessage("expected exactly one JSON line on stdout, got:%n%s", raw)
            .hasSize(1)
        return mapper.readValue(jsonLines.single(), Map::class.java) as Map<String, Any?>
    }
}
