package com.malai.payments.service

import com.malai.commons.model.Currency
import com.malai.payments.model.DepositsTotalResponse
import com.malai.payments.model.PaymentsStoppedTotalResponse
import com.malai.payments.repository.PaymentRepository
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Sums the Deposits that settled in a window, for whichever caller is composing a day-scoped figure.
 * The window arrives as two instants rather than a date because the *meaning* of a day belongs to
 * the caller (exec-service), not here — this context knows only when its rows settled.
 *
 * A Deposit is money that entered the bank from outside and landed in a Malai account: an INBOUND
 * payment, SETTLED, crediting a beneficiary account. That predicate is what excludes the cases the
 * headline number must never sweep in:
 *  - a **Book transfer** is OUTBOUND (one row, from the initiator's side), so it is never counted —
 *    otherwise a customer could inflate the bank's figure by moving their own money between their
 *    own accounts;
 *  - a direct-debit *collection* debits the customer and credits no Malai account, so it has no
 *    beneficiary and drops out — it is money leaving, not a deposit;
 *  - a card refund never becomes a payment row at all (it is a ledger credit owned by card-service),
 *    so it cannot reach this sum;
 *  - anything not yet settled is excluded by state.
 *
 * GBP only in v1: minor units of different currencies are not addable, so a non-GBP inbound payment
 * is excluded from the figure rather than summed into it. It is a flow figure keyed on settlement,
 * so a later Return does not retroactively reduce it.
 */
@Service
class InternalPaymentsMetricsService(
    private val paymentRepository: PaymentRepository,
) {

    fun depositsSettledBetween(from: Instant, until: Instant) = DepositsTotalResponse(
        currency = Currency.GBP.name,
        amountMinorUnits = paymentRepository.sumSettledInboundDeposits(Currency.GBP, from, until),
    )

    /**
     * Sums the money the bank stopped in a window: Payments in the `DECLINED` state, GBP only.
     *
     * `DECLINED` is the enacted result of an adverse Screening ruling — set in exactly one place, the
     * resolver enacting an actionable `DECLINE`. Sourcing the figure from the state rather than the
     * MDR is what makes it right: an *edit* to a DECLINE verdict declines the payment and so counts,
     * while a *reject* ruling leaves the payment parked in `REFERRED` and contributes nothing. Both
     * follow from reading `DECLINED`, without reimplementing the accept/edit/reject → actionable-output
     * translation that ADR-0009 keeps in one place. The link to the audit plane survives via the
     * payment's `screening_decision_id`, so drilling from the number into the Ops worklist reconciles.
     *
     * The window keys on the payment's `created_at` — the only timestamp the row carries — like every
     * other day-scoped Metric. In the demo a referral is declined in the same session it was raised,
     * so `created_at` coincides with the day the money was stopped; a dedicated decline timestamp is a
     * later refinement, not needed at demo scale.
     *
     * GBP only in v1: minor units of different currencies are not addable, so a non-GBP declined
     * payment is excluded from the figure rather than summed into it.
     */
    fun paymentsStoppedBetween(from: Instant, until: Instant) = PaymentsStoppedTotalResponse(
        currency = Currency.GBP.name,
        amountMinorUnits = paymentRepository.sumDeclinedPayments(Currency.GBP, from, until),
    )
}
