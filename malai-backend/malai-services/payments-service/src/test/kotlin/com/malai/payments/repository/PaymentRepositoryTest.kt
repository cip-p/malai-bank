package com.malai.payments.repository

import com.malai.commons.model.Currency
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentEntity
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import dev.kensa.Action
import dev.kensa.GivensContext
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

private val FIRST_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000007a1")
private val SECOND_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000007a2")
private val THIRD_PAYMENT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000007a3")

class PaymentRepositoryTest : PaymentsAcceptanceTest() {

    private fun aPaymentExists(
        paymentId: UUID,
        state: PaymentState,
        createdAt: String,
    ) = Action<GivensContext> {
        paymentRepository().save(
            PaymentEntity(
                paymentId = paymentId,
                rail = Rail.FPS,
                direction = PaymentDirection.OUTBOUND,
                state = state,
                debtorAccountId = someAccountUid(),
                beneficiarySortCode = malaiSortCode,
                beneficiaryAccountNo = "87654301",
                beneficiaryName = "Bob Harrison",
                amountMinorUnits = 1_000_000L,
                currency = Currency.GBP,
                endToEndId = UUID.randomUUID(),
                screeningDecisionId = UUID.randomUUID(),
                createdAt = Instant.parse(createdAt),
                createdByAccountHolderId = someAccountHolderUid(),
            ),
        )
    }

    private fun referredPayments() =
        paymentRepository().findByStateOrderByCreatedAtAsc(PaymentState.REFERRED).map { it.paymentId }

    @Test
    fun findByStateOrderByCreatedAtAscReturnsOnlyThePaymentsParkedInThatState() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.REFERRED, "2026-10-15T10:30:00Z"))
        and(aPaymentExists(SECOND_PAYMENT_ID, PaymentState.PENDING, "2026-10-15T10:31:00Z"))
        and(aPaymentExists(THIRD_PAYMENT_ID, PaymentState.DECLINED, "2026-10-15T10:32:00Z"))

        referredPayments() shouldBe listOf(FIRST_PAYMENT_ID)
    }

    @Test
    fun findByStateOrderByCreatedAtAscReturnsTheOldestParkedPaymentFirst() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.REFERRED, "2026-10-15T12:00:00Z"))
        and(aPaymentExists(SECOND_PAYMENT_ID, PaymentState.REFERRED, "2026-10-15T09:00:00Z"))

        referredPayments() shouldBe listOf(SECOND_PAYMENT_ID, FIRST_PAYMENT_ID)
    }

    @Test
    fun findByStateOrderByCreatedAtAscIsEmptyWhenNothingIsParked() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.SETTLED, "2026-10-15T10:30:00Z"))

        referredPayments().shouldBeEmpty()
    }

    private val windowFrom: Instant = Instant.parse("2026-07-19T23:00:00Z")
    private val windowUntil: Instant = Instant.parse("2026-07-20T23:00:00Z")

    private fun settledInWindow() =
        paymentRepository().findSettledInWindow(windowFrom, windowUntil).map { it.paymentId }

    @Test
    fun findSettledInWindowKeepsTheLowerBoundAndDropsTheUpperBound() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.SETTLED, "2026-07-19T23:00:00Z"))
        and(aPaymentExists(SECOND_PAYMENT_ID, PaymentState.SETTLED, "2026-07-19T22:59:59Z"))
        and(aPaymentExists(THIRD_PAYMENT_ID, PaymentState.SETTLED, "2026-07-20T23:00:00Z"))

        settledInWindow() shouldBe listOf(FIRST_PAYMENT_ID)
    }

    @Test
    fun findSettledInWindowExcludesEveryOtherLifecycleState() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.SETTLED, "2026-07-20T09:00:00Z"))
        and(aPaymentExists(UUID.randomUUID(), PaymentState.PENDING, "2026-07-20T10:00:00Z"))
        and(aPaymentExists(UUID.randomUUID(), PaymentState.REFERRED, "2026-07-20T11:00:00Z"))
        and(aPaymentExists(UUID.randomUUID(), PaymentState.DECLINED, "2026-07-20T12:00:00Z"))
        and(aPaymentExists(UUID.randomUUID(), PaymentState.RETURNED, "2026-07-20T13:00:00Z"))

        settledInWindow() shouldBe listOf(FIRST_PAYMENT_ID)
    }

    @Test
    fun findSettledInWindowReturnsTheOldestSettlementFirst() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.SETTLED, "2026-07-20T12:00:00Z"))
        and(aPaymentExists(SECOND_PAYMENT_ID, PaymentState.SETTLED, "2026-07-20T09:00:00Z"))

        settledInWindow() shouldBe listOf(SECOND_PAYMENT_ID, FIRST_PAYMENT_ID)
    }

    @Test
    fun findSettledInWindowIsEmptyWhenNothingSettledInsideIt() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aPaymentExists(FIRST_PAYMENT_ID, PaymentState.SETTLED, "2026-07-18T09:00:00Z"))

        settledInWindow().shouldBeEmpty()
    }

    private fun aRow(
        direction: PaymentDirection,
        rail: Rail,
        state: PaymentState,
        amountMinorUnits: Long,
        createdAt: String,
        currency: Currency = Currency.GBP,
        beneficiaryAccountId: UUID? = UUID.fromString("00000000-0000-4000-9000-0000000008b1"),
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
            ),
        )
    }

    private fun anInboundGbpCredit(amountMinorUnits: Long, createdAt: String) =
        aRow(PaymentDirection.INBOUND, Rail.FPS, PaymentState.SETTLED, amountMinorUnits, createdAt)

    private fun depositsTotal(): Long =
        paymentRepository().sumSettledInboundDeposits(Currency.GBP, windowFrom, windowUntil)

    @Test
    fun sumSettledInboundDepositsAddsOnlyInboundSettledGbpCreditsWithinTheHalfOpenWindow() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundGbpCredit(500_000, "2026-07-19T23:00:00Z"))
        and(anInboundGbpCredit(750_000, "2026-07-20T22:59:59Z"))
        and(anInboundGbpCredit(999_999, "2026-07-19T22:59:59Z"))
        and(anInboundGbpCredit(999_999, "2026-07-20T23:00:00Z"))

        depositsTotal() shouldBe 1_250_000L
    }

    @Test
    fun sumSettledInboundDepositsExcludesOutboundBookTransfersAndUnsettledAndNonGbpAndBeneficiarylessRows() =
        malaiAcceptanceTest {
            given(thereAreNoPaymentsInTheDatabase())
            and(anInboundGbpCredit(1_000_000, "2026-07-20T09:00:00Z"))
            and(aRow(PaymentDirection.OUTBOUND, Rail.BOOK_TRANSFER, PaymentState.SETTLED, 9_000_000, "2026-07-20T09:30:00Z"))
            and(aRow(PaymentDirection.INBOUND, Rail.FPS, PaymentState.PENDING, 2_000_000, "2026-07-20T10:00:00Z"))
            and(aRow(PaymentDirection.INBOUND, Rail.FPS, PaymentState.SETTLED, 3_000_000, "2026-07-20T10:30:00Z", currency = Currency.USD))
            and(aRow(
                PaymentDirection.INBOUND,
                Rail.BACS_DIRECT_DEBIT,
                PaymentState.SETTLED,
                4_000_000,
                "2026-07-20T11:00:00Z",
                beneficiaryAccountId = null,
                debtorAccountId = UUID.fromString("00000000-0000-4000-9000-0000000008c1"),
            ))

            depositsTotal() shouldBe 1_000_000L
        }

    @Test
    fun sumSettledInboundDepositsIsZeroWhenNothingQualifies() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(anInboundGbpCredit(1_000_000, "2026-07-18T09:00:00Z"))

        depositsTotal() shouldBe 0L
    }

    private fun aDeclinedGbpPayment(amountMinorUnits: Long, createdAt: String) =
        aRow(PaymentDirection.OUTBOUND, Rail.FPS, PaymentState.DECLINED, amountMinorUnits, createdAt, beneficiaryAccountId = null)

    private fun paymentsStoppedTotal(): Long =
        paymentRepository().sumDeclinedPayments(Currency.GBP, windowFrom, windowUntil)

    @Test
    fun sumDeclinedPaymentsAddsOnlyDeclinedGbpPaymentsWithinTheHalfOpenWindow() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedGbpPayment(500_000, "2026-07-19T23:00:00Z"))
        and(aDeclinedGbpPayment(750_000, "2026-07-20T22:59:59Z"))
        and(aDeclinedGbpPayment(999_999, "2026-07-19T22:59:59Z"))
        and(aDeclinedGbpPayment(999_999, "2026-07-20T23:00:00Z"))

        paymentsStoppedTotal() shouldBe 1_250_000L
    }

    @Test
    fun sumDeclinedPaymentsExcludesEveryOtherStateAndNonGbp() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedGbpPayment(1_000_000, "2026-07-20T09:00:00Z"))
        and(aRow(PaymentDirection.OUTBOUND, Rail.FPS, PaymentState.REFERRED, 2_000_000, "2026-07-20T09:30:00Z", beneficiaryAccountId = null))
        and(aRow(PaymentDirection.OUTBOUND, Rail.FPS, PaymentState.SETTLED, 3_000_000, "2026-07-20T10:00:00Z", beneficiaryAccountId = null))
        and(aRow(PaymentDirection.OUTBOUND, Rail.FPS, PaymentState.PENDING, 4_000_000, "2026-07-20T10:30:00Z", beneficiaryAccountId = null))
        and(aRow(PaymentDirection.OUTBOUND, Rail.FPS, PaymentState.DECLINED, 5_000_000, "2026-07-20T11:00:00Z", currency = Currency.USD, beneficiaryAccountId = null))

        paymentsStoppedTotal() shouldBe 1_000_000L
    }

    @Test
    fun sumDeclinedPaymentsIsZeroWhenNothingQualifies() = malaiAcceptanceTest {
        given(thereAreNoPaymentsInTheDatabase())
        and(aDeclinedGbpPayment(1_000_000, "2026-07-18T09:00:00Z"))

        paymentsStoppedTotal() shouldBe 0L
    }
}
