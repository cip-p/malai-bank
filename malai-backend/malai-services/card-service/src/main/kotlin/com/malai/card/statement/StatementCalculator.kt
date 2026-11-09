package com.malai.card.statement

import java.time.LocalDate
import java.time.YearMonth

data class StatementFigures(
    val statementBalanceMinorUnits: Long,
    val minimumPaymentMinorUnits: Long,
    val dueDate: LocalDate,
)

object StatementCalculator {

    private const val MINIMUM_PAYMENT_FLOOR_MINOR_UNITS = 500L
    private const val MINIMUM_PAYMENT_PER_MILLE = 25L
    private const val PER_MILLE_ROUNDING_OFFSET = 500L
    private const val PER_MILLE = 1_000L
    private const val DUE_DATE_DAY_OF_MONTH = 21

    fun figuresFor(closingBalanceMinorUnits: Long, cycleMonth: YearMonth): StatementFigures {
        val owed = -closingBalanceMinorUnits
        return StatementFigures(
            statementBalanceMinorUnits = owed,
            minimumPaymentMinorUnits = minimumPaymentFor(owed),
            dueDate = cycleMonth.plusMonths(1).atDay(DUE_DATE_DAY_OF_MONTH),
        )
    }

    private fun minimumPaymentFor(owed: Long): Long {
        if (owed <= 0L) return 0L
        val percentage = (owed * MINIMUM_PAYMENT_PER_MILLE + PER_MILLE_ROUNDING_OFFSET) / PER_MILLE
        return maxOf(MINIMUM_PAYMENT_FLOOR_MINOR_UNITS, percentage)
    }
}
