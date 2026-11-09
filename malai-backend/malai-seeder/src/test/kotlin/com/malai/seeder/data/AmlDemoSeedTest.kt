package com.malai.seeder.data

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The museum is only worth showing if the exhibits obey the rules they claim to have come from.
 * These are pure assertions over [AmlDemoSeed] — no Spring, no database: each seeded alert must be
 * one the live typology rules would genuinely have raised over its own evidence, and the derived
 * identities must be the ones `com.malai.aml.monitoring.AlertIdentity` would derive. Those formulas
 * are mirrored rather than shared (aml-service is off the seeder's classpath by architecture rule),
 * so the golden values below are what pins the mirror in place.
 */
class AmlDemoSeedTest {

    private val settledAt: Instant = Instant.parse("2026-07-26T09:00:00Z")

    @Test
    fun `an evidence payment's content hash is the sha256 of the row as monitoring reads it`() {
        val firstLeg = AmlDemoSeed.REPORTED_CASE.alerts.single().evidence.first()

        assertThat(firstLeg.paymentId).hasToString("00000000-0000-4000-8000-000023000010")
        assertThat(firstLeg.contentHashAt(settledAt))
            .isEqualTo("1c9eac6373bbeec87f36b27ae60775fc041e1cf42ff904a6699b419c169cdd24")
    }

    @Test
    fun `an alert's decision and entry ids derive from its account typology and evidence set`() {
        val reported = AmlDemoSeed.REPORTED_CASE
        val alert = reported.alerts.single()

        assertThat(alert.decisionId(reported.customer.currentAccountId))
            .isEqualTo(UUID.fromString("055518c3-fc9b-309c-8c84-6fa64f8fa85a"))
        assertThat(alert.entryId(reported.customer.currentAccountId))
            .isEqualTo(UUID.fromString("56e8d5fa-3b30-3419-8987-a8202dd09879"))
    }

    @Test
    fun `reordering the evidence set cannot change the alert it identifies`() {
        val reported = AmlDemoSeed.REPORTED_CASE
        val alert = reported.alerts.single()
        val reversed = alert.copy(evidence = alert.evidence.reversed())

        assertThat(reversed.decisionId(reported.customer.currentAccountId))
            .isEqualTo(alert.decisionId(reported.customer.currentAccountId))
    }

    @Test
    fun `every structuring alert satisfies the structuring rule it claims fired`() {
        structuringAlerts().forEach { (accountId, alert) ->
            assertThat(alert.evidence.size).describedAs("$accountId minimum payments").isGreaterThanOrEqualTo(3)
            assertThat(alert.evidence.map { it.amountMinorUnits })
                .describedAs("$accountId each leg is honestly below the threshold")
                .allMatch { it < AmlDemoSeed.STRUCTURING_THRESHOLD_MINOR_UNITS }
            assertThat(alert.evidence.sumOf { it.amountMinorUnits })
                .describedAs("$accountId jointly at or above the threshold")
                .isGreaterThanOrEqualTo(AmlDemoSeed.STRUCTURING_THRESHOLD_MINOR_UNITS)
            assertThat(alert.evidence.map { "${it.beneficiarySortCode} ${it.beneficiaryAccountNo}" }.distinct())
                .describedAs("$accountId one beneficiary")
                .hasSize(1)
            assertThat(alert.evidence.map { it.minutesAgo }.max() - alert.evidence.map { it.minutesAgo }.min())
                .describedAs("$accountId inside one rolling hour")
                .isLessThan(60L)
        }
    }

    @Test
    fun `each alert carries the risk score its own rule band produces`() {
        val threshold = AmlDemoSeed.STRUCTURING_THRESHOLD_MINOR_UNITS
        structuringAlerts().forEach { (accountId, alert) ->
            val joint = alert.evidence.sumOf { it.amountMinorUnits }
            val band = when {
                joint >= threshold * 5 -> 95
                joint >= threshold * 2 -> 85
                else -> 70
            }
            assertThat(alert.riskScore).describedAs("$accountId joint=$joint").isEqualTo(band)
        }

        assertThat(AmlDemoSeed.OPEN_CASE.alerts.single { it.typology == AmlDemoSeed.HIGH_RISK_CORRIDOR }.riskScore)
            .isEqualTo(80)
    }

    private fun structuringAlerts(): List<Pair<UUID, AmlDemoSeed.Alert>> =
        AmlDemoSeed.CASES.flatMap { monitoredCase ->
            monitoredCase.alerts
                .filter { it.typology == AmlDemoSeed.STRUCTURING }
                .map { monitoredCase.customer.currentAccountId to it }
        }

    @Test
    fun `the corridor alert's beneficiary jurisdiction is on the pinned high-risk list`() {
        val corridor = AmlDemoSeed.OPEN_CASE.alerts.single { it.typology == AmlDemoSeed.HIGH_RISK_CORRIDOR }
        val payment = corridor.evidence.single()

        assertThat(payment.rail).isEqualTo(AmlDemoSeed.SWIFT_RAIL)
        assertThat(payment.beneficiaryIban!!.take(2)).isEqualTo(corridor.jurisdiction)
        assertThat(payment.beneficiaryBic!!.substring(4, 6)).isEqualTo(corridor.jurisdiction)
        assertThat(AmlDemoSeed.JURISDICTION_LIST_CODES).contains(corridor.jurisdiction)
        assertThat(corridor.retrievedContext(settledAt).map { it.type })
            .describedAs("the list version that fired is pinned by content hash on the alert")
            .contains(AmlDemoSeed.JURISDICTION_LIST_CONTEXT_TYPE)
    }

    @Test
    fun `the museum's evidence sits outside the sweep window so it never competes with a live fire`() {
        assertThat(AmlDemoSeed.CASES.flatMap { it.evidence }.map { it.minutesAgo })
            .allMatch { it > 60L }
    }

    @Test
    fun `no two seeded rows share an identifier`() {
        val evidence = AmlDemoSeed.CASES.flatMap { it.evidence }
        assertThat(evidence.map { it.paymentId }).doesNotHaveDuplicates()
        assertThat(evidence.map { it.endToEndId }).doesNotHaveDuplicates()
        assertThat(evidence.map { it.paymentId } + evidence.map { it.endToEndId }).doesNotHaveDuplicates()

        val customers = AmlDemoSeed.MONITORED_CUSTOMERS
        assertThat(customers.flatMap {
            listOf(it.userId, it.accountHolderId, it.individualId, it.addressId, it.currentAccountId, it.caseId)
        }).doesNotHaveDuplicates()
        assertThat(customers.map { it.accountNumber }).doesNotHaveDuplicates()
        assertThat(customers.map { it.email }).doesNotHaveDuplicates()

        val alertIds = AmlDemoSeed.CASES.flatMap { monitoredCase ->
            monitoredCase.alerts.flatMap {
                listOf(
                    it.decisionId(monitoredCase.customer.currentAccountId),
                    it.entryId(monitoredCase.customer.currentAccountId),
                )
            } + listOfNotNull(monitoredCase.alerts.mapNotNull { it.rulingEntryId }).flatten()
        }
        assertThat(alertIds).doesNotHaveDuplicates()
    }

    @Test
    fun `the filed narrative cites only records on the case timeline`() {
        val reported = AmlDemoSeed.REPORTED_CASE
        val alert = reported.alerts.single()
        val sar = reported.sar!!
        val accountId = reported.customer.currentAccountId

        val statements = sar.statements(reported.caseId, accountId, settledAt, alert, settledAt)
        val cited = statements.flatMap { it.sources }.map { it.id }.toSet()
        val timeline = (listOf(alert.decisionId(accountId).toString()) +
            alert.evidence.map { it.paymentId.toString() }).toSet()

        assertThat(cited).isEqualTo(timeline)
        assertThat(sar.retrievedContext(alert, accountId, settledAt).map { it.id }.toSet()).isEqualTo(timeline)
        assertThat(AmlDemoSeed.narrativeOf(statements))
            .contains("account $accountId")
            .contains("case ${reported.caseId}")
            .contains("rulingInForce=ACCEPT")
    }
}
