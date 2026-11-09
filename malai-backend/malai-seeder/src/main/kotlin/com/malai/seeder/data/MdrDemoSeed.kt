package com.malai.seeder.data

import com.malai.mdr.model.HumanDispositionKind
import com.malai.mdr.model.HumanDispositionKind.ACCEPT
import com.malai.mdr.model.HumanDispositionKind.EDIT
import com.malai.mdr.model.HumanDispositionKind.REJECT
import com.malai.mdr.model.ModelDecisionOutcome
import com.malai.mdr.model.ModelDecisionOutcome.COMPLETED
import com.malai.mdr.model.ModelDecisionOutcome.ERRORED
import com.malai.mdr.model.ModelDecisionOutcome.TIMED_OUT
import com.malai.mdr.model.RetrievedContextRef
import java.util.UUID

/**
 * Demo MDR worklist: a fixed set of Model Decisions covering every Outcome and every ruling
 * state, so the ops pages have a live-looking worklist on first open. Appended through the MDR
 * service layer (chain append + audit double-entry + entry-id idempotency, exactly as production
 * appends), which makes seeding an append-only table compatible with clean's
 * leave-append-only-tables-alone posture: a re-seed replays the fixed entry ids and appends
 * nothing new.
 *
 * Entry / decision ids follow the pinned-UUID scheme with dedicated `0d` / `0e` / `0f` sections
 * (decision ids / decision entries / disposition entries). Correlation refs point at Sarah's
 * seeded estate so the ops pages tell a joined-up story.
 */
object MdrDemoSeed {

    const val MODEL_ID: String = "claude-opus-4-8"
    const val MODEL_VERSION: String = "2026-01"

    val FRAUD_TRIAGE_HASH: String = "a3f2".repeat(16)
    val CATEGORISATION_HASH: String = "b4e1".repeat(16)
    val INSIGHTS_HASH: String = "c5d0".repeat(16)

    data class DispositionSeed(
        val entryId: UUID,
        val disposition: HumanDispositionKind,
        val editedOutput: String? = null,
    )

    data class DecisionSeed(
        val entryId: UUID,
        val decisionId: UUID,
        val correlationType: String,
        val correlationId: String,
        val promptTemplateId: String,
        val promptTemplateVersion: String,
        val promptTemplateHash: String,
        val renderedPrompt: String,
        val retrievedContext: List<RetrievedContextRef>? = null,
        val outcome: ModelDecisionOutcome,
        val rawOutput: String? = null,
        val verdict: Map<String, Any?>? = null,
        val errorDetail: String? = null,
        val latencyMs: Long,
        val costMicros: Long,
        val dispositions: List<DispositionSeed> = emptyList(),
    )

    private fun decisionId(n: Int) = UUID.fromString("00000000-0000-4000-8000-00000d%06x".format(n))
    private fun decisionEntryId(n: Int) = UUID.fromString("00000000-0000-4000-8000-00000e%06x".format(n))
    private fun dispositionEntryId(n: Int) = UUID.fromString("00000000-0000-4000-8000-00000f%06x".format(n))

    val DECISIONS: List<DecisionSeed> = listOf(
        DecisionSeed(
            entryId = decisionEntryId(1),
            decisionId = decisionId(1),
            correlationType = "CARD_AUTHORISATION",
            correlationId = SarahMitchellSeed.PENDING_AUTH_1_ID.toString(),
            promptTemplateId = "fraud-triage",
            promptTemplateVersion = "v3",
            promptTemplateHash = FRAUD_TRIAGE_HASH,
            renderedPrompt = "Assess card authorisation ${SarahMitchellSeed.PENDING_AUTH_1_ID} " +
                "(Harbour Fuel, GBP 52.00) for fraud risk against the holder's 90-day history.",
            retrievedContext = listOf(
                RetrievedContextRef(
                    type = "AUTHORISATION_HISTORY",
                    id = "${SarahMitchellSeed.CURRENT_ACCOUNT_ID}-90d",
                    contentHash = "1a2b".repeat(16),
                ),
            ),
            outcome = COMPLETED,
            rawOutput = "APPROVE: spend pattern consistent with holder history; merchant seen twice in the last 90 days.",
            verdict = mapOf("decision" to "APPROVE", "riskScore" to 12),
            latencyMs = 842,
            costMicros = 1_500,
            dispositions = listOf(DispositionSeed(dispositionEntryId(1), ACCEPT)),
        ),
        DecisionSeed(
            entryId = decisionEntryId(2),
            decisionId = decisionId(2),
            correlationType = "ACCOUNT",
            correlationId = SarahMitchellSeed.CURRENT_ACCOUNT_ID.toString(),
            promptTemplateId = "transaction-categorisation",
            promptTemplateVersion = "v1",
            promptTemplateHash = CATEGORISATION_HASH,
            renderedPrompt = "Categorise the merchant 'Harbour Fuel' for account " +
                "${SarahMitchellSeed.CURRENT_ACCOUNT_ID} given MCC 5541 and the holder's category history.",
            outcome = COMPLETED,
            rawOutput = "category: groceries\nconfidence: 0.62\nnote: merchant name ambiguous",
            verdict = mapOf("category" to "GROCERIES", "confidence" to 0.62),
            latencyMs = 512,
            costMicros = 900,
            dispositions = listOf(
                DispositionSeed(
                    dispositionEntryId(2),
                    EDIT,
                    editedOutput = "category: fuel\nconfidence: 0.62\nnote: merchant name ambiguous",
                ),
            ),
        ),
        DecisionSeed(
            entryId = decisionEntryId(3),
            decisionId = decisionId(3),
            correlationType = "CARD_AUTHORISATION",
            correlationId = SarahMitchellSeed.PENDING_AUTH_2_ID.toString(),
            promptTemplateId = "fraud-triage",
            promptTemplateVersion = "v3",
            promptTemplateHash = FRAUD_TRIAGE_HASH,
            renderedPrompt = "Assess card authorisation ${SarahMitchellSeed.PENDING_AUTH_2_ID} " +
                "(Nimbus Groceries, GBP 18.75) for fraud risk against the holder's 90-day history.",
            outcome = COMPLETED,
            rawOutput = "DECLINE: velocity anomaly — third authorisation attempt at this merchant inside ten minutes.",
            verdict = mapOf("decision" to "DECLINE", "riskScore" to 87),
            latencyMs = 1_104,
            costMicros = 1_500,
            dispositions = listOf(DispositionSeed(dispositionEntryId(3), REJECT)),
        ),
        DecisionSeed(
            entryId = decisionEntryId(4),
            decisionId = decisionId(4),
            correlationType = "ACCOUNT",
            correlationId = SarahMitchellSeed.SAVINGS_ACCOUNT_ID.toString(),
            promptTemplateId = "spending-insights",
            promptTemplateVersion = "v2",
            promptTemplateHash = INSIGHTS_HASH,
            renderedPrompt = "Summarise savings behaviour for account ${SarahMitchellSeed.SAVINGS_ACCOUNT_ID} " +
                "over the last quarter for the monthly insights digest.",
            outcome = COMPLETED,
            rawOutput = "Savings balance held steady at GBP 8,400.00 with no withdrawals this quarter.",
            verdict = mapOf("insight" to "STABLE_SAVER"),
            latencyMs = 655,
            costMicros = 1_100,
            dispositions = listOf(
                DispositionSeed(dispositionEntryId(4), ACCEPT),
                DispositionSeed(dispositionEntryId(5), REJECT),
            ),
        ),
        DecisionSeed(
            entryId = decisionEntryId(5),
            decisionId = decisionId(5),
            correlationType = "ACCOUNT",
            correlationId = SarahMitchellSeed.CREDIT_ACCOUNT_ID.toString(),
            promptTemplateId = "fraud-triage",
            promptTemplateVersion = "v3",
            promptTemplateHash = FRAUD_TRIAGE_HASH,
            renderedPrompt = "Assess repayment pattern on credit account ${SarahMitchellSeed.CREDIT_ACCOUNT_ID} " +
                "for first-party fraud indicators.",
            outcome = TIMED_OUT,
            latencyMs = 30_000,
            costMicros = 0,
        ),
        DecisionSeed(
            entryId = decisionEntryId(6),
            decisionId = decisionId(6),
            correlationType = "ACCOUNT",
            correlationId = SarahMitchellSeed.CURRENT_ACCOUNT_ID.toString(),
            promptTemplateId = "spending-insights",
            promptTemplateVersion = "v2",
            promptTemplateHash = INSIGHTS_HASH,
            renderedPrompt = "Summarise current-account spending for account " +
                "${SarahMitchellSeed.CURRENT_ACCOUNT_ID} for the monthly insights digest.",
            outcome = ERRORED,
            errorDetail = "upstream provider returned 503 after 2 retries",
            latencyMs = 1_240,
            costMicros = 0,
        ),
        DecisionSeed(
            entryId = decisionEntryId(7),
            decisionId = decisionId(7),
            correlationType = "ACCOUNT",
            correlationId = SarahMitchellSeed.CURRENT_ACCOUNT_ID.toString(),
            promptTemplateId = "transaction-categorisation",
            promptTemplateVersion = "v1",
            promptTemplateHash = CATEGORISATION_HASH,
            renderedPrompt = "Categorise the merchant 'Fictional Coffee Co' for account " +
                "${SarahMitchellSeed.CURRENT_ACCOUNT_ID} given MCC 5814 and the holder's category history.",
            outcome = COMPLETED,
            rawOutput = "category: eating-out\nconfidence: 0.94",
            verdict = mapOf("category" to "EATING_OUT", "confidence" to 0.94),
            latencyMs = 488,
            costMicros = 900,
        ),
    )
}
