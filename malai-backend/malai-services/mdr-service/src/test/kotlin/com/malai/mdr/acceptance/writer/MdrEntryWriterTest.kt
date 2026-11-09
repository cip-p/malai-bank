package com.malai.mdr.acceptance.writer

import com.malai.commons.model.Currency
import com.malai.mdr.acceptance.MdrAcceptanceTest
import com.malai.mdr.service.MdrEntryWriter
import com.malai.mdr.service.RecordedHumanDisposition
import com.malai.mdr.service.RecordedModelDecision
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

class MdrEntryWriterTest : MdrAcceptanceTest() {

    private val writer: MdrEntryWriter get() = getBean(MdrEntryWriter::class.java)

    @Test
    fun recordIndependentlySurvivesTheCallerTransactionRollingBack() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        val txTemplate = TransactionTemplate(getBean(PlatformTransactionManager::class.java))
        runCatching {
            txTemplate.execute<Unit> {
                writer.recordIndependently(aCompletedDecision())
                throw IllegalStateException("simulated business-write failure after the model decision was recorded")
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
            (this[0]["outcome"] as String) shouldBe "COMPLETED"
            (this[0]["seq"] as Number).toLong() shouldBe 1L
        }
        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 1L
        }
    }

    @Test
    fun recordRollsBackWithTheCallerTransaction() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        val txTemplate = TransactionTemplate(getBean(PlatformTransactionManager::class.java))
        runCatching {
            txTemplate.execute<Unit> {
                writer.record(aCompletedDecision())
                throw IllegalStateException("simulated business-write failure after the model decision was recorded")
            }
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 0L
        }
    }

    @Test
    fun theDatabaseCheckRejectsACompletedDecisionMissingItsRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        shouldThrow<DataIntegrityViolationException> {
            writer.record(aCompletedDecision().copy(rawOutput = null))
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
        and(theMdrChainHeadInTheDatabase()) {
            (this["last_seq"] as Number).toLong() shouldBe 0L
        }
    }

    @Test
    fun theDatabaseCheckRejectsAnErroredDecisionMissingItsErrorDetail() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        shouldThrow<DataIntegrityViolationException> {
            writer.record(aCompletedDecision().copy(outcome = "ERRORED", rawOutput = null, errorDetail = null))
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun theDatabaseCheckRejectsATimedOutDecisionCarryingRawOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())

        shouldThrow<DataIntegrityViolationException> {
            writer.record(aCompletedDecision().copy(outcome = "TIMED_OUT", rawOutput = "a late answer"))
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 0
        }
    }

    @Test
    fun theDatabaseCheckRejectsAnEditDispositionMissingItsEditedOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        writer.record(aCompletedDecision())

        shouldThrow<DataIntegrityViolationException> {
            writer.recordDisposition(aDisposition(disposition = "EDIT", editedOutput = null))
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
        }
    }

    @Test
    fun theDatabaseCheckRejectsAnAcceptDispositionCarryingAnEditedOutput() = malaiAcceptanceTest {
        given(theMdrChainIsAtGenesis())
        writer.record(aCompletedDecision())

        shouldThrow<DataIntegrityViolationException> {
            writer.recordDisposition(aDisposition(disposition = "ACCEPT", editedOutput = "sneaky replacement"))
        }

        and(theMdrEntriesInTheDatabase()) {
            this shouldHaveSize 1
        }
    }

    private fun aDisposition(disposition: String, editedOutput: String?) = RecordedHumanDisposition(
        entryId = UUID.fromString("d1000000-0000-0000-0000-0000000000f1"),
        decisionId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
        parentEntryId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
        correlationType = "PAYMENT",
        correlationId = "pmt-writer-1",
        humanId = UUID.fromString("ad000000-0000-0000-0000-0000000000aa"),
        disposition = disposition,
        editedOutput = editedOutput,
    )

    private fun aCompletedDecision() = RecordedModelDecision(
        entryId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
        decisionId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
        correlationType = "PAYMENT",
        correlationId = "pmt-writer-1",
        advisory = false,
        modelId = "claude-opus-4-8",
        modelVersion = "2026-01",
        promptTemplateId = "fraud-triage",
        promptTemplateVersion = "v3",
        promptTemplateHash = ByteArray(32) { it.toByte() },
        renderedPrompt = "Assess payment pmt-writer-1 for fraud risk.",
        retrievedContext = null,
        outcome = "COMPLETED",
        rawOutput = "APPROVE: low risk.",
        verdict = null,
        errorDetail = null,
        latencyMs = 10L,
        costMicros = 100L,
        costCurrency = Currency.GBP,
    )
}
