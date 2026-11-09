package com.malai.commons.banking

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * Repo-wide lint: every literal `XX-XX-XX` sort-code-shaped string in the
 * active build must start with [MalaiSortCode.PREFIX]. Catches accidental
 * real-bank sort codes before they ship — Pitfall 2 (`RealSortCodeCollision`)
 * counter-measure.
 */
class MalaiSortCodePrefixLintTest {

    private val sortCodePattern = Regex("\\b(\\d{2}-\\d{2}-\\d{2})\\b")

    private val excludedSegments = listOf(
        "/target/",
        "/build/",
        "/.git/",
        "/.idea/",
        "/node_modules/",
        "/_imported-examples/",
        "/db/migration/", // Flyway migrations may reference allocated codes in comments
        "/banking/VALACDOS.txt", // upstream weight table contains allocated ranges in numeric form
        "/banking/SCSUBTAB.txt",
        // IBAN algorithm verification fixture: the ISO 13616 worked example uses
        // NatWest's published reference sort code + account `31926819` to verify
        // the mod-97 check digit calculation. Replacing it would invalidate the
        // reference IBAN `GB29NWBK60161331926819` and lose the
        // cross-implementation cross-check.
        "/identifier/IbanCalculatorTest.kt",
        // Plan 02-12 boot-time validation test: deliberately constructs a
        // non-MALAI BankIdentifierProperties to verify that AccountNumberGenerator's
        // @PostConstruct fails fast for a foreign prefix. The test fixture is
        // not a real-world sort code that ships to a buyer.
        "/identifier/AccountNumberGeneratorTest.kt",
        // Plan 02-14 acceptance tests for saved-payee / direct-debit / standing-order
        // services. These domains are inherently cross-bank: a saved payee, a DD
        // creditor, or a SO destination is by definition at ANOTHER UK bank, so the
        // test fixtures must use non-MALAI sort codes to realistically exercise the
        // controllers. Production code paths in these services do not ship literal
        // sort codes — only the tests do.
        "/payee-service/src/test/",
        "/direct-debit-service/src/test/",
        "/standing-order-service/src/test/",
        // sandbox-service acceptance tests drive reads of the stub's fictional
        // Counterparty catalogue, which by construction holds non-MALAI sort codes
        // (NatWest / HSBC / Lloyds / Monzo / Barclays / Starling). The service's
        // production code ships no literal sort codes — it forwards the stub catalogue.
        "/sandbox-service/src/test/",
        // fps-gateway acceptance tests drive payments to beneficiaries at other UK banks
        // (the gateway bridges to the external FPS scheme), so the test fixtures use
        // non-MALAI creditor sort codes by construction. The gateway's production code
        // ships no literal sort codes — it forwards whatever beneficiary it is handed.
        "/fps-gateway/src/test/",
        // bacs-gateway acceptance tests drive BACS Direct Credit inbound from an originator
        // (e.g. an employer) at another UK bank, so the test fixtures use a non-MALAI
        // originator sort code by construction. The gateway's production code ships no literal
        // sort codes — it forwards whatever scheme-shaped credit it is handed.
        "/bacs-gateway/src/test/",
        // Issue 09: registration-seeder's per-merchant clearing data (ACME / Tesco /
        // TfL / Octopus / Spotify / Eve Robinson) holds sort codes that point at
        // StubFictionalDirectory entries -- cross-bank by construction so federated
        // CoP and FPS-external can resolve the seeded payees / DDs / SOs.
        "/malai-registration-seeder/src/main/kotlin/com/malai/registrationseeder/data/ClearingAccountsSeed.kt",
        // Exec issue 07: the seeder's Banking-day cohort carries external counterparties by
        // construction -- inbound deposit originators (an employer's payroll bank) and outbound
        // FPS beneficiaries (a property / consulting / equipment payee's bank) are each at ANOTHER
        // UK bank, so their sort codes are deliberately non-MALAI. They are drawn from the
        // StubFictionalDirectory fictional-bank space (NatWest / HSBC / Lloyds / Monzo / Barclays /
        // Starling); the rows are seeded in terminal state (settled inbound deposits, DECLINED
        // outbound payments) and never resolved through CoP / FPS, so the codes are display-only.
        "/malai-seeder/src/main/kotlin/com/malai/seeder/data/BankingDaySeed.kt",
        // AML issue 09: the seeded AML museum's beneficiaries are the counterparties a structuring
        // pattern was placed with, at ANOTHER UK bank by construction — a pattern that never left
        // Malai would not be an AML typology. Drawn from the same StubFictionalDirectory space, and
        // the rows are seeded settled, never resolved through CoP or FPS.
        "/malai-seeder/src/main/kotlin/com/malai/seeder/data/AmlDemoSeed.kt",
        // E2E tests covering cross-bank domains — same rationale as the acceptance
        // exclusions above. A saved payee, a standing-order destination, and a
        // direct-debit creditor are each at ANOTHER UK bank by construction, so their
        // e2e fixtures use non-MALAI sort codes (the DD creditor in particular is a
        // StubFictionalDirectory counterparty the sandbox lodgement looks up by sort code).
        "/com/malai/test/e2e/tests/PayeeTest.kt",
        "/com/malai/test/e2e/tests/StandingOrderTest.kt",
        "/com/malai/test/e2e/tests/DirectDebitTest.kt",
        // InsightsTest's balance-forecast case seeds an active standing order whose payee
        // (a landlord) is at ANOTHER UK bank by construction — same cross-bank rationale as
        // StandingOrderTest above.
        "/com/malai/test/e2e/tests/InsightsTest.kt",
        // CoP federation E2E test: drives a destination that lives in
        // StubFictionalDirectory (the stub's NatWest sort code) so account-service
        // has to federate the lookup out to malai-stub. Same cross-bank rationale.
        "/com/malai/test/e2e/tests/CopFederationTest.kt",
        // PaymentsTest's cross-bank FPS test (Flow 3) drives a payment to a
        // destination at the stub's NatWest entry. Same cross-bank rationale.
        "/com/malai/test/e2e/tests/PaymentsTest.kt",
        // PaymentScreeningTest pays the screening watchlist's counterparty, who is a
        // StubFictionalDirectory entry at Lloyds. Same cross-bank rationale as PaymentsTest.
        "/com/malai/test/e2e/tests/PaymentScreeningTest.kt",
        // AmlTest structures five sub-threshold payments to one beneficiary, who must be at
        // ANOTHER UK bank for the payments to leave over FPS and settle — it reuses
        // PaymentsTest's NatWest destination. Same cross-bank rationale.
        "/com/malai/test/e2e/tests/AmlTest.kt",
        // CopilotExplanationTest explains a payment that screening referred, so it pays the
        // same watchlisted Lloyds counterparty to earn the referral. Same rationale.
        "/com/malai/test/e2e/tests/CopilotExplanationTest.kt",
        // The Copilot's redaction tests assert that a beneficiary account number recorded on
        // the audit plane is masked while the sort code beside it is shown in full — which
        // needs a counterparty at ANOTHER bank to be the case worth asserting. Same
        // watchlisted Lloyds counterparty as CopilotExplanationTest.
        "/com/malai/copilot/acceptance/happy/ReferredPaymentExplanationTest.kt",
        "/com/malai/copilot/model/CopilotRedactionTest.kt",
        // SandboxTest's inbound-payment scenario drives an originator at the stub's
        // NatWest entry crediting a malai customer — same cross-bank rationale as
        // PaymentsTest's inbound flow.
        "/com/malai/test/e2e/tests/SandboxTest.kt",
        // KensaEnvelopeOrderTest exercises the FPS-return envelope sequence by
        // sending to the stub's NatWest "Jane Closed" RETURNS fixture. Same
        // cross-bank rationale as PaymentsTest.
        "/com/malai/test/e2e/tests/KensaEnvelopeOrderTest.kt",
        // malai-stub IS the simulator of other UK banks (NatWest, HSBC, Lloyds,
        // Monzo, Barclays …). Every sort code in its fictional directory is
        // deliberately non-MALAI — that's the whole point. Its own tests and
        // the paired stub-client + stub-client-test-support modules share the
        // same fictional sort codes.
        "/malai-stub/",
        "/malai-stub-client/",
        "/malai-stub-client-test-support/",
        // Issue 09: the internal direct-debit client + its test-support stub the sandbox
        // mandate-lodgement round trip, whose creditor is a Counterparty at another UK bank by
        // construction — cross-bank fixtures, same rationale as the stub-client pair above.
        "/malai-direct-debit-client/",
        "/malai-direct-debit-client-test-support/",
        // Reference docs, planning materials, and similar "this is what other
        // banks use" prose live outside the code path that ships to a buyer.
        "/CLAUDE.md",
        "/docs/",
    )

    private val includedExtensions = listOf(".kt", ".java", ".sql", ".json", ".yaml", ".yml", ".properties")

    @Test
    fun `every literal sort code in the active build starts with the MALAI prefix`() {
        val backendRoot = findBackendRoot()
        val violations = mutableListOf<String>()

        Files.walk(backendRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) }
                .filter { p ->
                    val s = p.toString()
                    excludedSegments.none { s.contains(it) } &&
                        includedExtensions.any { s.endsWith(it) }
                }
                .forEach { p -> scanFile(p, violations) }
        }

        assertThat(violations)
            .withFailMessage(
                "Sort codes found that do not start with %s. Either fix them or extend the exclusion list:\n%s",
                MalaiSortCode.PREFIX,
                violations.joinToString("\n"),
            )
            .isEmpty()
    }

    private fun scanFile(path: Path, violations: MutableList<String>) {
        Files.lines(path, Charsets.UTF_8).use { lines ->
            var lineNumber = 0
            lines.forEach { line ->
                lineNumber++
                sortCodePattern.findAll(line).forEach { match ->
                    val candidate = match.groupValues[1]
                    if (!MalaiSortCode.isMalaiPrefix(candidate)) {
                        violations += "${path}:$lineNumber: $candidate"
                    }
                }
            }
        }
    }

    private fun findBackendRoot(): Path {
        // Tests run from `<backend>/malai-commons/malai-commons/`. Walk up until
        // we find the malai-backend root — identified by the presence of
        // `malai-services/` AND `malai-api/` AND `pom.xml`.
        var current: File = File(System.getProperty("user.dir")).absoluteFile
        repeat(8) {
            if (current.resolve("malai-services").isDirectory &&
                current.resolve("malai-api").isDirectory &&
                current.resolve("pom.xml").isFile
            ) {
                return current.toPath()
            }
            current = current.parentFile ?: return current.toPath()
        }
        return current.toPath()
    }
}
