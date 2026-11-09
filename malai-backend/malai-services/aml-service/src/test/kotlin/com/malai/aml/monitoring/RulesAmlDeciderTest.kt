package com.malai.aml.monitoring

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RulesAmlDeciderTest {

    private val properties = AmlProperties()
    private val decider = RulesAmlDecider(properties, HighRiskJurisdictionList(properties))

    private val accountId = UUID.fromString("00000000-0000-4000-9000-00000000acc1")
    private val until = Instant.parse("2026-10-15T10:30:00Z")
    private val from = until.minus(properties.structuring.window)

    @Test
    fun honestlyNamesTheDeciderThatActuallyRan() {
        decider.modelId shouldBe "malai-aml-rules"
        decider.modelVersion shouldBe "1"
    }

    @Test
    fun fiveSubThresholdPaymentsToOneBeneficiaryJointlyAtTheThresholdFire() {
        val payments = (1..5).map { aPayment(amountMinorUnits = 200_000L, minutesBeforeWindowEnd = it.toLong()) }

        val hits = decide(payments)

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.STRUCTURING)
        hits[0].riskScore shouldBe 70
        hits[0].evidence shouldContainExactly payments.sortedBy { it.createdAt }
    }

    @Test
    fun twoPaymentsJointlyAtTheThresholdAreTooFewToFire() {
        val hits = decide((1..2).map { aPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = it.toLong()) })

        hits.shouldBeEmpty()
    }

    @Test
    fun aJointSumOnePennyUnderTheThresholdDoesNotFire() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 333_333L, minutesBeforeWindowEnd = 1),
                aPayment(amountMinorUnits = 333_333L, minutesBeforeWindowEnd = 2),
                aPayment(amountMinorUnits = 333_333L, minutesBeforeWindowEnd = 3),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun aPaymentAtTheScreeningThresholdDoesNotJoinThePattern() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 1),
                aPayment(amountMinorUnits = 200_000L, minutesBeforeWindowEnd = 2),
                aPayment(amountMinorUnits = 200_000L, minutesBeforeWindowEnd = 3),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun paymentsToDifferentBeneficiariesDoNotCombine() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 1),
                aPayment(amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 2),
                aPayment(amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 3, beneficiaryAccountNo = "11111111"),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun ibanBeneficiariesGroupByNormalisedIban() {
        val hits = decide(
            listOf(
                aPayment(
                    amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 1,
                    beneficiarySortCode = null, beneficiaryAccountNo = null, beneficiaryIban = "DE89 3704 0044 0532 0130 00",
                ),
                aPayment(
                    amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 2,
                    beneficiarySortCode = null, beneficiaryAccountNo = null, beneficiaryIban = "de89370400440532013000",
                ),
                aPayment(
                    amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 3,
                    beneficiarySortCode = null, beneficiaryAccountNo = null, beneficiaryIban = "DE89370400440532013000",
                ),
            ),
        )

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.STRUCTURING)
    }

    @Test
    fun paymentsLackingBeneficiaryCoordinatesNeverGroup() {
        val hits = decide(
            (1..5).map {
                aPayment(
                    amountMinorUnits = 400_000L, minutesBeforeWindowEnd = it.toLong(),
                    beneficiarySortCode = null, beneficiaryAccountNo = null, beneficiaryIban = null,
                )
            },
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun aPaymentAtTheWindowStartJoinsThePattern() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 500_000L, createdAt = from),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 1),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 2),
            ),
        )

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.STRUCTURING)
    }

    @Test
    fun aPaymentAtTheWindowEndIsOutsideTheHalfOpenWindow() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 500_000L, createdAt = until),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 1),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 2),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun inboundPaymentsDoNotJoinStructuring() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = 1, direction = INBOUND),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 2),
                aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 3),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun eachStructuredBeneficiaryRaisesItsOwnHit() {
        val hits = decide(
            (1..3).map { aPayment(amountMinorUnits = 400_000L, minutesBeforeWindowEnd = it.toLong()) } +
                (4..6).map {
                    aPayment(
                        amountMinorUnits = 400_000L, minutesBeforeWindowEnd = it.toLong(),
                        beneficiarySortCode = "65-82-99", beneficiaryAccountNo = "22222222",
                    )
                },
        )

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.STRUCTURING, AmlTypology.STRUCTURING)
        hits[0].evidence.map { it.beneficiaryKey }.toSet() shouldBe setOf("SC:658237:87654331")
        hits[1].evidence.map { it.beneficiaryKey }.toSet() shouldBe setOf("SC:658299:22222222")
    }

    @Test
    fun theRiskScoreScalesWithTheJointAmount() {
        val hits = decide((1..4).map { aPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = it.toLong()) })

        hits[0].riskScore shouldBe 85
    }

    @Test
    fun aSwiftPaymentToAListedJurisdictionRaisesACorridorHitPinningTheListVersion() {
        val payment = aSwiftPayment(beneficiaryIban = "ZZ42MALA06580231234567", beneficiaryBic = "BARCGB22")

        val hits = decide(listOf(payment))

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.HIGH_RISK_CORRIDOR)
        hits[0].riskScore shouldBe 80
        hits[0].evidence shouldContainExactly listOf(payment)
        hits[0].detail shouldBe mapOf("jurisdiction" to "ZZ")
        hits[0].contextPins shouldContainExactly listOf(
            ContextPin("JURISDICTION_LIST", "2026-07-01", sha256Hex("2026-07-01\nQM\nZZ")),
        )
    }

    @Test
    fun theJurisdictionDerivesFromBicCharactersFiveAndSixWhenTheIbanIsAbsent() {
        val hits = decide(listOf(aSwiftPayment(beneficiaryBic = "BANKQMAA")))

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.HIGH_RISK_CORRIDOR)
        hits[0].detail shouldBe mapOf("jurisdiction" to "QM")
    }

    @Test
    fun aLowercaseIbanStillDerivesItsJurisdiction() {
        val hits = decide(listOf(aSwiftPayment(beneficiaryIban = "zz42mala06580231234567")))

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.HIGH_RISK_CORRIDOR)
        hits[0].detail shouldBe mapOf("jurisdiction" to "ZZ")
    }

    @Test
    fun aSwiftPaymentToAnUnlistedJurisdictionDoesNotFire() {
        val hits = decide(listOf(aSwiftPayment(beneficiaryIban = "DE89370400440532013000")))

        hits.shouldBeEmpty()
    }

    @Test
    fun aDomesticRailPaymentNeverRaisesACorridorHit() {
        val hits = decide(
            listOf(
                aPayment(
                    amountMinorUnits = 2_500_000L, rail = "FPS",
                    beneficiarySortCode = null, beneficiaryAccountNo = null,
                    beneficiaryIban = "ZZ42MALA06580231234567",
                ),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun anInboundSwiftPaymentFromAListedJurisdictionDoesNotFire() {
        val hits = decide(listOf(aSwiftPayment(direction = INBOUND, beneficiaryIban = "ZZ42MALA06580231234567")))

        hits.shouldBeEmpty()
    }

    @Test
    fun aSwiftPaymentLackingIbanAndBicNeverFlags() {
        val hits = decide(listOf(aSwiftPayment()))

        hits.shouldBeEmpty()
    }

    @Test
    fun aCorridorPaymentAtTheWindowEndIsOutsideTheHalfOpenWindow() {
        val hits = decide(listOf(aSwiftPayment(beneficiaryIban = "ZZ42MALA06580231234567", createdAt = until)))

        hits.shouldBeEmpty()
    }

    @Test
    fun anInboundCreditDrainedOutboundAtExactlyTheConfiguredPercentageFires() {
        val inbound = anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20)
        val firstDrain = aPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = 15)
        val secondDrain = aPayment(amountMinorUnits = 300_000L, minutesBeforeWindowEnd = 10)

        val hits = decide(listOf(inbound, firstDrain, secondDrain))

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.RAPID_IN_OUT)
        hits[0].riskScore shouldBe 75
        hits[0].evidence shouldContainExactly listOf(inbound, firstDrain, secondDrain)
        hits[0].detail shouldBe mapOf("inboundPaymentId" to inbound.paymentId.toString(), "drainedPercent" to 80)
    }

    @Test
    fun aDrainOneMinorUnitUnderTheConfiguredPercentageDoesNotFire() {
        val hits = decide(
            listOf(
                anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20),
                aPayment(amountMinorUnits = 799_999L, minutesBeforeWindowEnd = 10),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun anOutboundAtExactlyTheInOutWindowEndIsOutsideTheHalfOpenWindow() {
        val hits = decide(
            listOf(
                anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20),
                aPayment(amountMinorUnits = 900_000L, minutesBeforeWindowEnd = 5),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun anOutboundAtTheSameInstantAsTheInboundCountsTowardsTheDrain() {
        val inbound = anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 10)
        val drain = aPayment(amountMinorUnits = 900_000L, minutesBeforeWindowEnd = 10)

        val hits = decide(listOf(inbound, drain))

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.RAPID_IN_OUT)
        hits[0].evidence shouldContainExactly listOf(inbound, drain)
    }

    @Test
    fun anOutboundBeforeTheInboundNeverCountsTowardsTheDrain() {
        val hits = decide(
            listOf(
                aPayment(amountMinorUnits = 900_000L, minutesBeforeWindowEnd = 25),
                anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20),
            ),
        )

        hits.shouldBeEmpty()
    }

    @Test
    fun aFullDrainScoresHigherThanAPartialOne() {
        val hits = decide(
            listOf(
                anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20),
                aPayment(amountMinorUnits = 600_000L, minutesBeforeWindowEnd = 15),
                aPayment(amountMinorUnits = 400_000L, minutesBeforeWindowEnd = 10),
            ),
        )

        hits.map { it.typology } shouldContainExactly listOf(AmlTypology.RAPID_IN_OUT)
        hits[0].riskScore shouldBe 90
        hits[0].detail["drainedPercent"] shouldBe 100
    }

    @Test
    fun anInboundCreditWithNoFollowingOutboundIsQuiet() {
        val hits = decide(listOf(anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 20)))

        hits.shouldBeEmpty()
    }

    @Test
    fun twoDrainedInboundCreditsRaiseSeparateHitsWithTheirOwnEvidence() {
        val firstInbound = anInboundPayment(amountMinorUnits = 1_000_000L, minutesBeforeWindowEnd = 30)
        val firstDrain = aPayment(amountMinorUnits = 900_000L, minutesBeforeWindowEnd = 25)
        val secondInbound = anInboundPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = 10)
        val secondDrain = aPayment(amountMinorUnits = 500_000L, minutesBeforeWindowEnd = 9)

        val hits = decide(listOf(firstInbound, firstDrain, secondInbound, secondDrain))

        hits.map { it.typology } shouldContainExactly
            listOf(AmlTypology.RAPID_IN_OUT, AmlTypology.RAPID_IN_OUT)
        hits[0].evidence shouldContainExactly listOf(firstInbound, firstDrain)
        hits[1].evidence shouldContainExactly listOf(secondInbound, secondDrain)
    }

    @Test
    fun timesOutOnTheArmedAmount() {
        shouldThrow<AmlDeciderTimedOutException> {
            decide(listOf(aPayment(amountMinorUnits = properties.armedTimeoutAmountMinorUnits)))
        }
    }

    @Test
    fun erroredOnTheArmedAmount() {
        shouldThrow<IllegalStateException> {
            decide(listOf(aPayment(amountMinorUnits = properties.armedErrorAmountMinorUnits)))
        }
    }

    private fun decide(payments: List<MonitoredPayment>): List<TypologyHit> =
        decider.decide(AmlWindow(accountId, from, until, payments), "a rendered prompt")

    private fun anInboundPayment(
        amountMinorUnits: Long,
        minutesBeforeWindowEnd: Long,
    ): MonitoredPayment = aPayment(
        amountMinorUnits = amountMinorUnits,
        minutesBeforeWindowEnd = minutesBeforeWindowEnd,
        direction = INBOUND,
        beneficiarySortCode = null,
        beneficiaryAccountNo = null,
    )

    private fun aSwiftPayment(
        amountMinorUnits: Long = 2_500_000L,
        minutesBeforeWindowEnd: Long = 1,
        direction: String = "OUTBOUND",
        beneficiaryIban: String? = null,
        beneficiaryBic: String? = null,
        createdAt: Instant = until.minusSeconds(minutesBeforeWindowEnd * 60),
    ): MonitoredPayment = aPayment(
        amountMinorUnits = amountMinorUnits,
        direction = direction,
        rail = "SWIFT",
        beneficiarySortCode = null,
        beneficiaryAccountNo = null,
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
        createdAt = createdAt,
    )

    private fun aPayment(
        amountMinorUnits: Long,
        minutesBeforeWindowEnd: Long = 1,
        direction: String = "OUTBOUND",
        rail: String = "FPS",
        beneficiarySortCode: String? = "65-82-37",
        beneficiaryAccountNo: String? = "87654331",
        beneficiaryIban: String? = null,
        beneficiaryBic: String? = null,
        createdAt: Instant = until.minusSeconds(minutesBeforeWindowEnd * 60),
    ): MonitoredPayment = MonitoredPayment(
        paymentId = UUID.randomUUID(),
        accountId = accountId,
        direction = direction,
        rail = rail,
        amountMinorUnits = amountMinorUnits,
        currency = "GBP",
        createdAt = createdAt,
        beneficiaryName = "Bob Harrison",
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNo = beneficiaryAccountNo,
        beneficiaryIban = beneficiaryIban,
        beneficiaryBic = beneficiaryBic,
    )
}
