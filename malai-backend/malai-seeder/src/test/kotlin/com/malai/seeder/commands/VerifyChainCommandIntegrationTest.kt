package com.malai.seeder.commands

import com.malai.commons.model.Currency
import com.malai.mdr.service.MdrEntryWriter
import com.malai.mdr.service.RecordedModelDecision
import com.malai.seeder.testapp.TestSeederApplication
import com.malai.security.audit.ActorType
import com.malai.security.audit.AuditEvent
import com.malai.security.audit.AuditWriter
import org.assertj.core.api.Assertions.assertThat
import com.malai.sql.MalaiSchema
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * CLI-module coverage for the scope-generalised [VerifyChainCommand] (MDR issue 04). Runs against
 * the pre-provisioned `malai_test` Postgres (see `start-docker-containers-local.sh`) with both the
 * audit ('global') and MDR ('mdr') chains seeded to a single intact row, then proves the command
 * exits 0 when every scope verifies and non-zero when either chain is tampered out-of-band.
 */
@SpringBootTest(classes = [TestSeederApplication::class])
@ActiveProfiles("test")
class VerifyChainCommandIntegrationTest {

    @Autowired private lateinit var verifyChainCommand: VerifyChainCommand
    @Autowired private lateinit var auditWriter: AuditWriter
    @Autowired private lateinit var mdrEntryWriter: MdrEntryWriter
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val entryId = UUID.fromString("cccc0000-0000-0000-0000-000000000001")
    private val decisionId = UUID.fromString("cccc0000-0000-0000-0000-000000000002")

    companion object {
        @JvmStatic
        @AfterAll
        fun handTheDatabaseBackAtPostMigrationState() = MalaiSchema.resetToMigratedState()
    }

    @BeforeEach
    fun resetBothChainsToGenesisAndAppendOneRowEach() {
        jdbc.update("DELETE FROM mdr_entries")
        jdbc.update("DELETE FROM audit_events")
        resetChainScopeToGenesis("global")
        resetChainScopeToGenesis("mdr")

        auditWriter.record(AuditEvent(actorId = UUID.randomUUID(), actorType = ActorType.SYSTEM, action = "VERIFY-CHAIN-IT"))
        mdrEntryWriter.record(aCompletedModelDecision())
    }

    @Test
    fun `exits 0 when every chain scope is intact`() {
        assertThat(verifyChainCommand.call()).isEqualTo(0)
    }

    @Test
    fun `exits non-zero when the mdr chain is tampered`() {
        jdbc.update("UPDATE mdr_entries SET current_hash = ? WHERE seq = 1", ByteArray(32) { 0xFF.toByte() })

        assertThat(verifyChainCommand.call()).isNotEqualTo(0)
    }

    @Test
    fun `exits non-zero when the audit chain is tampered`() {
        jdbc.update("UPDATE audit_events SET current_hash = ? WHERE seq = 1", ByteArray(32) { 0xFF.toByte() })

        assertThat(verifyChainCommand.call()).isNotEqualTo(0)
    }

    private fun resetChainScopeToGenesis(scope: String) {
        jdbc.update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES (?, E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
            scope,
        )
    }

    private fun aCompletedModelDecision() = RecordedModelDecision(
        entryId = entryId,
        decisionId = decisionId,
        correlationType = "PAYMENT",
        correlationId = "pmt-verify-chain",
        advisory = false,
        modelId = "claude-opus-4-8",
        modelVersion = "2026-01",
        promptTemplateId = "fraud-triage",
        promptTemplateVersion = "v3",
        promptTemplateHash = ByteArray(32) { 0x01 },
        renderedPrompt = "Assess payment for fraud risk.",
        retrievedContext = null,
        outcome = "COMPLETED",
        rawOutput = "APPROVE: low risk.",
        verdict = null,
        errorDetail = null,
        latencyMs = 10L,
        costMicros = 5L,
        costCurrency = Currency.GBP,
    )
}
