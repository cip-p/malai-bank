package com.malai.payments.acceptance.happy

import com.malai.commons.model.Currency
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.GivensContext
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalPaymentsMetricsControllerTest : PaymentsAcceptanceTest() {

    private val windowStart = "2026-07-19T23:00:00Z"
    private val windowEnd = "2026-07-20T23:00:00Z"

    @Test
    fun inboundSettledGbpCreditsAreSummed() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 500_000, createdAt = "2026-07-20T09:00:00Z"))
        and(anInboundCredit(amountMinorUnits = 750_000, createdAt = "2026-07-20T11:00:00Z"))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_250_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aBookTransferContributesNothingBecauseNothingEnteredTheBankFromOutside() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.OUTBOUND,
            rail = Rail.BOOK_TRANSFER,
            state = PaymentState.SETTLED,
            amountMinorUnits = 9_000_000,
            createdAt = "2026-07-20T10:00:00Z",
            beneficiaryAccountId = anotherAccountUid(),
        ))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aDirectDebitCollectionContributesNothingBecauseItDebitsTheCustomer() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.INBOUND,
            rail = Rail.BACS_DIRECT_DEBIT,
            state = PaymentState.SETTLED,
            amountMinorUnits = 3_000_000,
            createdAt = "2026-07-20T10:00:00Z",
            beneficiaryAccountId = null,
            debtorAccountId = someAccountUid(),
        ))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun anInboundCreditThatHasNotSettledContributesNothing() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.INBOUND,
            rail = Rail.FPS,
            state = PaymentState.PENDING,
            amountMinorUnits = 2_000_000,
            createdAt = "2026-07-20T10:00:00Z",
        ))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aNonGbpInboundCreditIsExcludedRatherThanSummedIntoTheGbpFigure() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.INBOUND,
            rail = Rail.FPS,
            state = PaymentState.SETTLED,
            currency = Currency.USD,
            amountMinorUnits = 5_000_000,
            createdAt = "2026-07-20T10:00:00Z",
        ))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aCreditJustEitherSideOfLondonMidnightFallsOnTheCorrectDay() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 400_000, createdAt = "2026-07-19T22:59:59Z"))
        and(anInboundCredit(amountMinorUnits = 600_000, createdAt = "2026-07-19T23:00:00Z"))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 600_000
                noExtraProps()
            }
        }
    }

    @Test
    fun theWindowEndIsExclusiveSoTheNextDaysFirstCreditIsNotCounted() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 800_000, createdAt = "2026-07-20T22:59:59Z"))
        and(anInboundCredit(amountMinorUnits = 999_999, createdAt = "2026-07-20T23:00:00Z"))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 800_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aQuietWindowSumsZeroRatherThanFailing() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundCredit(amountMinorUnits = 1_000_000, createdAt = "2026-07-18T09:00:00Z"))

        whenever(paymentsServiceReceives(depositsTotalBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    @Test
    fun declinedGbpPaymentsAreSummed() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedPayment(amountMinorUnits = 800_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aDeclinedPayment(amountMinorUnits = 200_000, createdAt = "2026-07-20T11:00:00Z"))

        whenever(paymentsServiceReceives(paymentsStoppedBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aPaymentStillParkedInReferredContributesNothing() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedPayment(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.OUTBOUND,
            rail = Rail.FPS,
            state = PaymentState.REFERRED,
            amountMinorUnits = 4_000_000,
            createdAt = "2026-07-20T10:00:00Z",
        ))

        whenever(paymentsServiceReceives(paymentsStoppedBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aNonGbpDeclinedPaymentIsExcludedRatherThanSummedIntoTheGbpFigure() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedPayment(amountMinorUnits = 1_000_000, createdAt = "2026-07-20T09:00:00Z"))
        and(aPayment(
            direction = PaymentDirection.OUTBOUND,
            rail = Rail.SWIFT,
            state = PaymentState.DECLINED,
            currency = Currency.USD,
            amountMinorUnits = 5_000_000,
            createdAt = "2026-07-20T10:00:00Z",
        ))

        whenever(paymentsServiceReceives(paymentsStoppedBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 1_000_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aDeclineJustEitherSideOfLondonMidnightFallsOnTheCorrectDay() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedPayment(amountMinorUnits = 400_000, createdAt = "2026-07-19T22:59:59Z"))
        and(aDeclinedPayment(amountMinorUnits = 600_000, createdAt = "2026-07-19T23:00:00Z"))

        whenever(paymentsServiceReceives(paymentsStoppedBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 600_000
                noExtraProps()
            }
        }
    }

    @Test
    fun aQuietWindowStopsNothingRatherThanFailing() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedPayment(amountMinorUnits = 1_000_000, createdAt = "2026-07-18T09:00:00Z"))

        whenever(paymentsServiceReceives(paymentsStoppedBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 0
                noExtraProps()
            }
        }
    }

    private fun aDeclinedPayment(amountMinorUnits: Long, createdAt: String) = aPayment(
        direction = PaymentDirection.OUTBOUND,
        rail = Rail.FPS,
        state = PaymentState.DECLINED,
        amountMinorUnits = amountMinorUnits,
        createdAt = createdAt,
    )

    private fun anInboundCredit(amountMinorUnits: Long, createdAt: String) = aPayment(
        direction = PaymentDirection.INBOUND,
        rail = Rail.FPS,
        state = PaymentState.SETTLED,
        amountMinorUnits = amountMinorUnits,
        createdAt = createdAt,
        beneficiaryAccountId = someAccountUid(),
    )

    private fun aPayment(
        direction: PaymentDirection,
        rail: Rail,
        state: PaymentState,
        amountMinorUnits: Long,
        createdAt: String,
        currency: Currency = Currency.GBP,
        beneficiaryAccountId: UUID? = someAccountUid(),
        debtorAccountId: UUID? = null,
    ) = Action<GivensContext> {
        paymentRepository().save(
            PaymentEntity(
                paymentId = UUID.randomUUID(),
                rail = rail,
                direction = direction,
                state = state,
                debtorAccountId = debtorAccountId,
                beneficiaryAccountId = beneficiaryAccountId,
                amountMinorUnits = amountMinorUnits,
                currency = currency,
                endToEndId = UUID.randomUUID(),
                createdAt = Instant.parse(createdAt),
                createdByAccountHolderId = someAccountHolderUid(),
            ),
        )
    }

    private fun depositsTotalBetween(from: String, until: String) =
        get()
            .url("/api/v1/internal/payments/deposits-total")
            .queryParam("from", from)
            .queryParam("until", until)
            .withServiceJwtToken()

    private fun paymentsStoppedBetween(from: String, until: String) =
        get()
            .url("/api/v1/internal/payments/stopped-total")
            .queryParam("from", from)
            .queryParam("until", until)
            .withServiceJwtToken()
}
