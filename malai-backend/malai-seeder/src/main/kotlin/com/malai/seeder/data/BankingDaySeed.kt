package com.malai.seeder.data

import java.time.LocalDate
import java.util.UUID

/**
 * A plausible **Banking day** for the Exec dashboard, seeded so the four headline Metrics read a
 * small bank's morning on a fresh reset rather than zeroes — and so the one number an evaluator
 * drills into, Payments stopped, reconciles with the Ops worklist.
 *
 * Two halves:
 *
 *  - [NEW_CUSTOMERS] — a compact cohort of customers onboarded *today* (created-at set to run
 *    time by [com.malai.seeder.commands.SeedCommand]). Each carries a minimal-but-real estate and
 *    one inbound settled deposit, so *New customers* counts the holders, *Cards issued* counts
 *    their debit cards, and *Deposits* sums their credits. They are not login personas (no Cognito
 *    identity), only demo-day statistics with genuine rows.
 *
 *  - [DECLINED_PAYMENTS] — the screening story, seeded end to end. Each is an outbound FPS payment
 *    from Sarah Mitchell that Screening referred and a human ruled adversely: a `PAYMENT`-correlated
 *    Model Decision recorded under the real decider ([MODEL_ID] version [MODEL_VERSION]) with the
 *    genuine [PROMPT_TEMPLATE_HASH] pin, an `ACCEPT` disposition authored by the seeded ops
 *    reviewer, and the payment landed in `DECLINED`. The payment row carries the Model Decision's
 *    id in `screening_decision_id`, so *Payments stopped* and the worklist are two readings of the
 *    same facts. Seeded in the terminal `DECLINED` state directly — hold released, no ledger
 *    entries — matching what the resolver would produce, since payments-service (and its resolver)
 *    is off the seeder's classpath.
 *
 * Pinned UUIDs continue the fixture scheme in dedicated sections ([SECTION_CUSTOMER] /
 * [SECTION_DECLINED]) so a reset reproduces the dataset byte-for-byte, and the fixed MDR entry ids
 * make the screening decisions replay idempotently, exactly like [MdrDemoSeed].
 */
object BankingDaySeed {

    const val MODEL_ID: String = "malai-screening-rules"
    const val MODEL_VERSION: String = "1"
    const val PROMPT_TEMPLATE_ID: String = "payment-screening"
    const val PROMPT_TEMPLATE_VERSION: String = "v1"
    const val PROMPT_TEMPLATE_HASH: String = "6214a34422b7d5cd8868d93111a6074593b8428acc3a552c28671e2c1ff8afaf"
    const val WATCHLIST_VERSION: String = "2026-07-01"

    const val AMOUNT_AT_OR_ABOVE_THRESHOLD: String = "AMOUNT_AT_OR_ABOVE_THRESHOLD"
    const val WATCHLIST_COUNTERPARTY: String = "WATCHLIST_COUNTERPARTY"

    val ACCOUNT_CONTEXT_HASH: String = "5e1a".repeat(16)
    val WATCHLIST_CONTEXT_HASH: String = "9c7b".repeat(16)

    data class NewCustomer(
        val index: Int,
        val email: String,
        val firstName: String,
        val lastName: String,
        val dateOfBirth: LocalDate,
        val phone: String,
        val accountNumber: String,
        val addressLine1: String,
        val postTown: String,
        val postCode: String,
        val cardPanSeed: Long,
        val depositAmountMinor: Long,
        val depositOriginatorName: String,
        val depositOriginatorSortCode: String,
        val depositOriginatorAccountNo: String,
        val depositDescription: String,
    ) {
        val fullName: String get() = "$firstName $lastName"
        val userId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 0)
        val accountHolderId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 1)
        val individualId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 2)
        val addressId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 3)
        val currentAccountId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 4)
        val cardId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 5)
        val depositPaymentId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 6)
        val depositEndToEndId: UUID get() = bankingDayId(SECTION_CUSTOMER, index * SLOTS + 7)
    }

    data class DeclinedPayment(
        val index: Int,
        val amountMinor: Long,
        val beneficiaryName: String,
        val beneficiarySortCode: String,
        val beneficiaryAccountNo: String,
        val description: String,
        val riskScore: Int,
        val reasons: List<String>,
        val latencyMs: Long,
    ) {
        val paymentId: UUID get() = bankingDayId(SECTION_DECLINED, index * SLOTS + 0)
        val endToEndId: UUID get() = bankingDayId(SECTION_DECLINED, index * SLOTS + 1)
        val decisionId: UUID get() = bankingDayId(SECTION_DECLINED, index * SLOTS + 2)
        val decisionEntryId: UUID get() = bankingDayId(SECTION_DECLINED, index * SLOTS + 3)
        val dispositionEntryId: UUID get() = bankingDayId(SECTION_DECLINED, index * SLOTS + 4)

        val verdict: Map<String, Any?>
            get() = mapOf("decision" to "DECLINE", "riskScore" to riskScore, "reasons" to reasons)

        val renderedPrompt: String
            get() = "Assess outbound FPS payment to $beneficiaryName " +
                "($beneficiarySortCode $beneficiaryAccountNo) for GBP ${pounds(amountMinor)} " +
                "against Malai's referral threshold and the $WATCHLIST_VERSION watchlist."
    }

    val NEW_CUSTOMERS: List<NewCustomer> = listOf(
        NewCustomer(
            index = 1,
            email = "emma.clarke@example.com",
            firstName = "Emma",
            lastName = "Clarke",
            dateOfBirth = LocalDate.of(1990, 3, 14),
            phone = "+44 20 7946 0002",
            accountNumber = "12345682",
            addressLine1 = "3 Willow Court",
            postTown = "Bristol",
            postCode = "BS1 4TT",
            cardPanSeed = 20_260_720L,
            depositAmountMinor = 245_000L,
            depositOriginatorName = "Meridian Payroll Ltd",
            depositOriginatorSortCode = "40-00-02",
            depositOriginatorAccountNo = "44556677",
            depositDescription = "Salary",
        ),
        NewCustomer(
            index = 2,
            email = "james.okafor@example.com",
            firstName = "James",
            lastName = "Okafor",
            dateOfBirth = LocalDate.of(1985, 11, 2),
            phone = "+44 20 7946 0003",
            accountNumber = "12345683",
            addressLine1 = "18 Maple Way",
            postTown = "Leeds",
            postCode = "LS2 7HG",
            cardPanSeed = 20_260_721L,
            depositAmountMinor = 187_550L,
            depositOriginatorName = "Harrow Logistics Ltd",
            depositOriginatorSortCode = "20-00-05",
            depositOriginatorAccountNo = "33445566",
            depositDescription = "Invoice 20481",
        ),
        NewCustomer(
            index = 3,
            email = "aisha.khan@example.com",
            firstName = "Aisha",
            lastName = "Khan",
            dateOfBirth = LocalDate.of(1992, 7, 19),
            phone = "+44 20 7946 0004",
            accountNumber = "12345684",
            addressLine1 = "7 Rowan Street",
            postTown = "Manchester",
            postCode = "M1 3BX",
            cardPanSeed = 20_260_722L,
            depositAmountMinor = 320_000L,
            depositOriginatorName = "Brightwater Consulting",
            depositOriginatorSortCode = "30-00-03",
            depositOriginatorAccountNo = "77889900",
            depositDescription = "Contract payment",
        ),
        NewCustomer(
            index = 4,
            email = "thomas.reid@example.com",
            firstName = "Thomas",
            lastName = "Reid",
            dateOfBirth = LocalDate.of(1979, 1, 27),
            phone = "+44 20 7946 0005",
            accountNumber = "12345685",
            addressLine1 = "52 Birch Avenue",
            postTown = "Edinburgh",
            postCode = "EH3 9QL",
            cardPanSeed = 20_260_723L,
            depositAmountMinor = 98_000L,
            depositOriginatorName = "Cedar and Co",
            depositOriginatorSortCode = "60-00-01",
            depositOriginatorAccountNo = "11002233",
            depositDescription = "Refund",
        ),
        NewCustomer(
            index = 5,
            email = "sofia.marino@example.com",
            firstName = "Sofia",
            lastName = "Marino",
            dateOfBirth = LocalDate.of(1995, 9, 8),
            phone = "+44 20 7946 0006",
            accountNumber = "12345686",
            addressLine1 = "9 Elm Terrace",
            postTown = "Cardiff",
            postCode = "CF10 2DA",
            cardPanSeed = 20_260_724L,
            depositAmountMinor = 264_000L,
            depositOriginatorName = "Northbank Studios",
            depositOriginatorSortCode = "04-00-04",
            depositOriginatorAccountNo = "66778899",
            depositDescription = "Freelance fee",
        ),
    )

    val DECLINED_PAYMENTS: List<DeclinedPayment> = listOf(
        DeclinedPayment(
            index = 1,
            amountMinor = 1_250_000L,
            beneficiaryName = "Northgate Holdings Ltd",
            beneficiarySortCode = "23-00-06",
            beneficiaryAccountNo = "55667788",
            description = "Property deposit",
            riskScore = 80,
            reasons = listOf(AMOUNT_AT_OR_ABOVE_THRESHOLD),
            latencyMs = 1_180,
        ),
        DeclinedPayment(
            index = 2,
            amountMinor = 480_000L,
            beneficiaryName = "Carol Singh",
            beneficiarySortCode = "30-00-03",
            beneficiaryAccountNo = "11223344",
            description = "Consulting fee",
            riskScore = 40,
            reasons = listOf(WATCHLIST_COUNTERPARTY),
            latencyMs = 1_340,
        ),
        DeclinedPayment(
            index = 3,
            amountMinor = 1_500_000L,
            beneficiaryName = "Meridian Trading Co",
            beneficiarySortCode = "40-00-02",
            beneficiaryAccountNo = "99887766",
            description = "Equipment purchase",
            riskScore = 80,
            reasons = listOf(AMOUNT_AT_OR_ABOVE_THRESHOLD),
            latencyMs = 990,
        ),
    )

    val DEPOSITS_TOTAL_MINOR: Long = NEW_CUSTOMERS.sumOf { it.depositAmountMinor }
    val PAYMENTS_STOPPED_TOTAL_MINOR: Long = DECLINED_PAYMENTS.sumOf { it.amountMinor }

    private const val SECTION_CUSTOMER: Int = 0x20
    private const val SECTION_DECLINED: Int = 0x21
    private const val SLOTS: Int = 16
}

private fun bankingDayId(section: Int, n: Int): UUID =
    UUID.fromString("00000000-0000-4000-8000-0000%02x%06x".format(section, n))

private fun pounds(amountMinor: Long): String =
    "${amountMinor / 100}.${(amountMinor % 100).toString().padStart(2, '0')}"
