package com.malai.seeder.data

import com.malai.mdr.model.RetrievedContextRef
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * The AML museum: two Cases a self-guided evaluator finds already on the worklist, each carrying
 * exhibits the real pipeline would have produced.
 *
 *  - [PRIYA] is the finished arc. One `STRUCTURING` alert over five sub-threshold payments to one
 *    beneficiary, an `ACCEPT` ruling on it, a SAR draft recorded as its own Model Decision, an
 *    `ACCEPT` ruling on that, and the Case closed `CLOSED_REPORTED` with the ruled narrative
 *    attached — the whole of issues 03 through 07 visible without reproducing a typology first.
 *  - [MARCUS] is the same story mid-flight: a `STRUCTURING` alert and a `HIGH_RISK_CORRIDOR` alert,
 *    neither ruled, on an `OPEN` case. The officer's next action is the demo's next click.
 *
 * Both are login personas seeded the established way (admin-created, permanent password), because
 * the tipping-off invariant is only demonstrable if you can log in as the customer under
 * investigation and see an ordinary bank app. Their evidence payments post to the ledger too, so
 * the customer's own feed shows the same payments the case file cites.
 *
 * **Recording paths.** Every chain-bearing write goes through the real service: alert and SAR
 * Model Decisions and their Human Dispositions through [com.malai.mdr.service.MdrDecisionService] /
 * [com.malai.mdr.service.MdrDispositionService], case transitions through
 * [com.malai.security.audit.AuditWriter]. Only the `aml_cases` row is written by JDBC — aml-service
 * is off the seeder's classpath by architecture rule (`onlyTheAggregatorDependsOn`: nothing outside
 * `com.malai.aml` and the `com.malai.api` aggregator may depend on it), the same reason
 * [BankingDaySeed]'s payments are written by JDBC. `aml_cases` carries no hash chain, so chain
 * verification still covers everything it is meant to.
 *
 * **Derived, not invented.** Alert decision and entry ids are derived here by the same formulas
 * `com.malai.aml.monitoring.AlertIdentity` uses — sha256 over (account, typology, sorted evidence
 * payment ids), then a name-based UUID — and each evidence ref carries the genuine content hash of
 * its payment row under `MonitoredPayment`'s formula. A seeded alert is therefore bit-identical to
 * the one a live sweep would raise over the same payments: re-detection would dedup against it
 * rather than double-raise. Those two formulas are mirrored, not shared, for the architectural
 * reason above; aml-service's CONTEXT.md records the coupling.
 *
 * The evidence payments sit two to five hours back — inside one rolling window when they were made,
 * outside the current one — so the museum never competes with a live-fired pattern (see
 * `docs/aml-live-fire-demo.md`). [PRIYA] is also the live-fire persona: her Case is terminal, so a
 * freshly fired pattern opens a new one, and her funding credit leaves headroom to fire it.
 *
 * Pinned UUIDs continue [BankingDaySeed]'s scheme in dedicated sections. The prompt-template hashes
 * are the genuine sha256 of aml-service's template resources; the rendered prompts and the SAR
 * narrative are fixture prose in the shape the deciders produce, exactly as [BankingDaySeed] pins
 * a rendered screening prompt.
 */
object AmlDemoSeed {

    const val ALERT_MODEL_ID: String = "malai-aml-rules"
    const val ALERT_MODEL_VERSION: String = "1"
    const val ALERT_PROMPT_TEMPLATE_ID: String = "aml-monitoring"
    const val ALERT_PROMPT_TEMPLATE_VERSION: String = "v1"
    const val ALERT_PROMPT_TEMPLATE_HASH: String =
        "0e6903cb7766c4484a00fdc0817926cf65499157a74dec5234114df156ee5c08"

    const val SAR_MODEL_ID: String = "malai-sar-template"
    const val SAR_MODEL_VERSION: String = "1"
    const val SAR_PROMPT_TEMPLATE_ID: String = "sar-narrative"
    const val SAR_PROMPT_TEMPLATE_VERSION: String = "v1"
    const val SAR_PROMPT_TEMPLATE_HASH: String =
        "e179fcc705c7970bec59c6ea5bcb82ba4ef335dfe9e0a3a616443ada3ed2b29e"

    const val ACCOUNT_CORRELATION_TYPE: String = "ACCOUNT"
    const val CASE_CORRELATION_TYPE: String = "CASE"
    const val PAYMENT_CONTEXT_TYPE: String = "PAYMENT"
    const val ALERT_DECISION_CONTEXT_TYPE: String = "ALERT_DECISION"
    const val JURISDICTION_LIST_CONTEXT_TYPE: String = "JURISDICTION_LIST"

    const val STRUCTURING: String = "STRUCTURING"
    const val HIGH_RISK_CORRIDOR: String = "HIGH_RISK_CORRIDOR"

    const val STRUCTURING_THRESHOLD_MINOR_UNITS: Long = 1_000_000L

    const val JURISDICTION_LIST_VERSION: String = "2026-07-01"
    val JURISDICTION_LIST_CODES: List<String> = listOf("QM", "ZZ")
    val JURISDICTION_LIST_CONTENT_HASH: String =
        sha256Hex((listOf(JURISDICTION_LIST_VERSION) + JURISDICTION_LIST_CODES).joinToString("\n"))

    const val CASE_OPEN: String = "OPEN"
    const val CASE_CLOSED_REPORTED: String = "CLOSED_REPORTED"

    data class MonitoredCustomer(
        val index: Int,
        val email: String,
        val firstName: String,
        val lastName: String,
        val dateOfBirth: LocalDate,
        val phone: String,
        val demoPassword: String,
        val accountNumber: String,
        val addressLine1: String,
        val postTown: String,
        val postCode: String,
        val seed: Long,
        val fundingMinorUnits: Long,
        val fundingCounterpartyName: String,
        val fundingDescription: String,
        val fundingDaysAgo: Long,
    ) {
        val fullName: String get() = "$firstName $lastName"
        val userId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 0)
        val accountHolderId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 1)
        val individualId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 2)
        val addressId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 3)
        val currentAccountId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 4)
        val caseId: UUID get() = amlId(SECTION_MONITORED, index * SLOTS + 5)
    }

    data class EvidencePayment(
        val index: Int,
        val rail: String,
        val amountMinorUnits: Long,
        val beneficiaryName: String,
        val beneficiarySortCode: String? = null,
        val beneficiaryAccountNo: String? = null,
        val beneficiaryIban: String? = null,
        val beneficiaryBic: String? = null,
        val description: String,
        val minutesAgo: Long,
    ) {
        val paymentId: UUID get() = amlId(SECTION_EVIDENCE, index * SLOTS + 0)
        val endToEndId: UUID get() = amlId(SECTION_EVIDENCE, index * SLOTS + 1)

        // Truncated to what Postgres stores. The cited content hash covers the settlement instant,
        // so it must be the instant a reader of the row sees, not a nanosecond-precision clock's.
        fun createdAt(now: Instant): Instant =
            now.minus(minutesAgo, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS)

        fun contentHash(now: Instant): String = contentHashAt(createdAt(now))

        fun contentHashAt(settledAt: Instant): String = sha256Hex(
            listOf(
                paymentId.toString(),
                rail,
                DIRECTION_OUTBOUND,
                amountMinorUnits.toString(),
                CURRENCY,
                settledAt.toString(),
                beneficiaryName,
                beneficiarySortCode.orEmpty(),
                beneficiaryAccountNo.orEmpty(),
                beneficiaryIban.orEmpty(),
                beneficiaryBic.orEmpty(),
            ).joinToString("|"),
        )

        fun promptLine(now: Instant): String =
            "  - $paymentId | $DIRECTION_OUTBOUND $rail | $amountMinorUnits minor units of $CURRENCY | " +
                "at ${createdAt(now)} | to $beneficiaryName ${maskedBeneficiary()}"

        fun maskedBeneficiary(): String = when {
            beneficiarySortCode != null && beneficiaryAccountNo != null ->
                "$beneficiarySortCode ${maskIdentifier(beneficiaryAccountNo)}"
            beneficiaryIban != null -> listOfNotNull(beneficiaryBic, maskIdentifier(beneficiaryIban)).joinToString(" ")
            else -> "not supplied"
        }
    }

    data class Alert(
        val typology: String,
        val riskScore: Int,
        val evidence: List<EvidencePayment>,
        val latencyMs: Long,
        val jurisdiction: String? = null,
        val rulingEntryId: UUID? = null,
    ) {
        fun evidenceHash(accountId: UUID): String = sha256Hex(
            "$accountId\n$typology\n${evidence.map { it.paymentId.toString() }.sorted().joinToString("\n")}",
        )

        fun decisionId(accountId: UUID): UUID = nameUuid("aml-alert-decision:${evidenceHash(accountId)}")

        fun entryId(accountId: UUID): UUID = nameUuid("aml-alert-entry:${evidenceHash(accountId)}")

        fun verdict(): Map<String, Any?> = buildMap {
            put("typology", typology)
            put("riskScore", riskScore)
            put("evidencePaymentIds", evidence.map { it.paymentId.toString() })
            jurisdiction?.let { put("jurisdiction", it) }
        }

        fun retrievedContext(now: Instant): List<RetrievedContextRef> =
            evidence.map {
                RetrievedContextRef(
                    type = PAYMENT_CONTEXT_TYPE,
                    id = it.paymentId.toString(),
                    contentHash = it.contentHash(now),
                )
            } + listOfNotNull(
                jurisdiction?.let {
                    RetrievedContextRef(
                        type = JURISDICTION_LIST_CONTEXT_TYPE,
                        id = JURISDICTION_LIST_VERSION,
                        contentHash = JURISDICTION_LIST_CONTENT_HASH,
                    )
                },
            )

        fun renderedPrompt(accountId: UUID, now: Instant): String =
            "Evaluate account $accountId's settled payments in the rolling window for " +
                "money-laundering typologies, against the $ALERT_PROMPT_TEMPLATE_ID " +
                "$ALERT_PROMPT_TEMPLATE_VERSION rules: STRUCTURING at or above " +
                "$STRUCTURING_THRESHOLD_MINOR_UNITS minor units jointly, RAPID_IN_OUT, and " +
                "HIGH_RISK_CORRIDOR against jurisdiction list $JURISDICTION_LIST_VERSION " +
                "(${JURISDICTION_LIST_CODES.joinToString(", ")}).\n" +
                "SETTLED PAYMENTS\n" +
                evidence.joinToString("\n") { it.promptLine(now) }
    }

    data class SarSource(val type: String, val id: String)

    data class SarStatement(val text: String, val sources: List<SarSource>)

    data class SarDraft(
        val decisionId: UUID,
        val entryId: UUID,
        val rulingEntryId: UUID,
        val latencyMs: Long,
    ) {
        fun renderedPrompt(caseId: UUID, accountId: UUID, openedAt: Instant): String =
            "Draft the narrative section of a Suspicious Activity Report for case $caseId on " +
                "account $accountId, opened at $openedAt, citing only the records on the case " +
                "timeline. Every Statement must cite the records it rests on by their (type, id)."

        fun statements(caseId: UUID, accountId: UUID, openedAt: Instant, alert: Alert, now: Instant):
            List<SarStatement> {
            val alertSource = SarSource(ALERT_DECISION_CONTEXT_TYPE, alert.decisionId(accountId).toString())
            val paymentSources = alert.evidence.map { SarSource(PAYMENT_CONTEXT_TYPE, it.paymentId.toString()) }
            return listOf(
                SarStatement(
                    text = "Malai Bank reports activity it considers suspicious on account $accountId, " +
                        "investigated as case $caseId opened at $openedAt.",
                    sources = listOf(alertSource) + paymentSources,
                ),
                SarStatement(
                    text = "Transaction monitoring raised an alert on this account: " +
                        "${alertSummary(alert)}.",
                    sources = listOf(alertSource),
                ),
            ) + alert.evidence.map {
                SarStatement(
                    text = "The account settled a payment at ${it.createdAt(now)}: ${paymentSummary(it)}.",
                    sources = listOf(SarSource(PAYMENT_CONTEXT_TYPE, it.paymentId.toString())),
                )
            }
        }

        fun retrievedContext(alert: Alert, accountId: UUID, now: Instant): List<RetrievedContextRef> =
            listOf(
                RetrievedContextRef(
                    type = ALERT_DECISION_CONTEXT_TYPE,
                    id = alert.decisionId(accountId).toString(),
                    contentHash = sha256Hex(
                        "$ALERT_DECISION_CONTEXT_TYPE|${alert.decisionId(accountId)}|${alertSummary(alert)}",
                    ),
                ),
            ) + alert.evidence.map {
                RetrievedContextRef(
                    type = PAYMENT_CONTEXT_TYPE,
                    id = it.paymentId.toString(),
                    contentHash = it.contentHash(now),
                )
            }
    }

    fun narrativeOf(statements: List<SarStatement>): String = statements.joinToString("\n\n") { it.text }

    fun recordedVerdict(statements: List<SarStatement>): Map<String, Any?> = mapOf(
        "narrative" to narrativeOf(statements),
        "statements" to statements.map { statement ->
            mapOf(
                "text" to statement.text,
                "sources" to statement.sources.map { mapOf("type" to it.type, "id" to it.id) },
            )
        },
    )

    private fun alertSummary(alert: Alert): String = listOf(
        "typology=${alert.typology}",
        "riskScore=${alert.riskScore}",
        "rulingInForce=ACCEPT",
        "evidenceCount=${alert.evidence.size}",
    ).joinToString(" ")

    private fun paymentSummary(payment: EvidencePayment): String = listOf(
        "rail=${payment.rail}",
        "direction=$DIRECTION_OUTBOUND",
        "amountMinorUnits=${payment.amountMinorUnits}",
        "currency=$CURRENCY",
        "beneficiaryName=${payment.beneficiaryName}",
        "beneficiary=${payment.maskedBeneficiary()}",
    ).joinToString(" ")

    data class MonitoredCase(
        val customer: MonitoredCustomer,
        val status: String,
        val alerts: List<Alert>,
        val sar: SarDraft? = null,
    ) {
        val caseId: UUID get() = customer.caseId
        val evidence: List<EvidencePayment> get() = alerts.flatMap { it.evidence }
        val alertDecisionIds: List<UUID> get() = alerts.map { it.decisionId(customer.currentAccountId) }
    }

    val PRIYA: MonitoredCustomer = MonitoredCustomer(
        index = 1,
        email = "priya.raghunathan@example.com",
        firstName = "Priya",
        lastName = "Raghunathan",
        dateOfBirth = LocalDate.of(1987, 5, 30),
        phone = "+44 20 7946 0007",
        demoPassword = "DemoAmlReported!2026",
        accountNumber = "12345687",
        addressLine1 = "24 Kingsland Row",
        postTown = "London",
        postCode = "E8 2LT",
        seed = 20_260_725L,
        fundingMinorUnits = 3_000_000L,
        fundingCounterpartyName = "Halbrook Property Partners",
        fundingDescription = "Property sale proceeds",
        fundingDaysAgo = 3L,
    )

    val MARCUS: MonitoredCustomer = MonitoredCustomer(
        index = 2,
        email = "marcus.delacroix@example.com",
        firstName = "Marcus",
        lastName = "Delacroix",
        dateOfBirth = LocalDate.of(1981, 12, 4),
        phone = "+44 20 7946 0008",
        demoPassword = "DemoAmlOpen!2026",
        accountNumber = "12345688",
        addressLine1 = "6 Quayside Walk",
        postTown = "Southampton",
        postCode = "SO14 3TT",
        seed = 20_260_726L,
        fundingMinorUnits = 2_500_000L,
        fundingCounterpartyName = "Verrell Trading Ltd",
        fundingDescription = "Consultancy settlement",
        fundingDaysAgo = 3L,
    )

    private val PRIYA_STRUCTURING: Alert = Alert(
        typology = STRUCTURING,
        riskScore = 70,
        evidence = (1..5).map { leg ->
            EvidencePayment(
                index = leg,
                rail = FPS_RAIL,
                amountMinorUnits = 200_000L,
                beneficiaryName = "Dominic Fairweather",
                beneficiarySortCode = "60-00-01",
                beneficiaryAccountNo = "44112255",
                description = "Transfer",
                minutesAgo = 190L - (leg - 1) * 10L,
            )
        },
        latencyMs = 42L,
        rulingEntryId = amlId(SECTION_RULING, 1),
    )

    private val MARCUS_STRUCTURING: Alert = Alert(
        typology = STRUCTURING,
        riskScore = 70,
        evidence = (1..4).map { leg ->
            EvidencePayment(
                index = 10 + leg,
                rail = FPS_RAIL,
                amountMinorUnits = 260_000L,
                beneficiaryName = "Lorna Ashworth",
                beneficiarySortCode = "04-00-04",
                beneficiaryAccountNo = "77335599",
                description = "Invoice settlement",
                minutesAgo = 260L - (leg - 1) * 15L,
            )
        },
        latencyMs = 37L,
    )

    private val MARCUS_CORRIDOR: Alert = Alert(
        typology = HIGH_RISK_CORRIDOR,
        riskScore = 80,
        evidence = listOf(
            EvidencePayment(
                index = 20,
                rail = SWIFT_RAIL,
                amountMinorUnits = 750_000L,
                beneficiaryName = "Ostrava Freight SA",
                beneficiaryIban = "QM64FCTB0000009988776655",
                beneficiaryBic = "FCTBQM22XXX",
                description = "Freight charter",
                minutesAgo = 200L,
            ),
        ),
        latencyMs = 29L,
        jurisdiction = "QM",
    )

    val REPORTED_CASE: MonitoredCase = MonitoredCase(
        customer = PRIYA,
        status = CASE_CLOSED_REPORTED,
        alerts = listOf(PRIYA_STRUCTURING),
        sar = SarDraft(
            decisionId = amlId(SECTION_SAR, 1),
            entryId = amlId(SECTION_SAR, 2),
            rulingEntryId = amlId(SECTION_SAR, 3),
            latencyMs = 61L,
        ),
    )

    val OPEN_CASE: MonitoredCase = MonitoredCase(
        customer = MARCUS,
        status = CASE_OPEN,
        alerts = listOf(MARCUS_STRUCTURING, MARCUS_CORRIDOR),
    )

    val CASES: List<MonitoredCase> = listOf(REPORTED_CASE, OPEN_CASE)

    val MONITORED_CUSTOMERS: List<MonitoredCustomer> = CASES.map { it.customer }

    const val DIRECTION_OUTBOUND: String = "OUTBOUND"
    const val CURRENCY: String = "GBP"
    const val FPS_RAIL: String = "FPS"
    const val SWIFT_RAIL: String = "SWIFT"

    private const val SECTION_MONITORED: Int = 0x22
    private const val SECTION_EVIDENCE: Int = 0x23
    private const val SECTION_SAR: Int = 0x24
    private const val SECTION_RULING: Int = 0x25
    private const val SLOTS: Int = 16
}

private fun amlId(section: Int, n: Int): UUID =
    UUID.fromString("00000000-0000-4000-8000-0000%02x%06x".format(section, n))

private fun nameUuid(name: String): UUID = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8))

private fun maskIdentifier(identifier: String): String =
    "*".repeat(maxOf(0, identifier.length - 4)) + identifier.takeLast(4)

private fun sha256Hex(content: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
