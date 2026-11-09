package com.malai.card.statement

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.YearMonth

class StatementCalculatorTest {

    private val anyCycle = YearMonth.of(2026, 5)

    @Test
    fun `the statement balance is the owed amount, the negated closing ledger balance`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -40_000, cycleMonth = anyCycle)
            .statementBalanceMinorUnits shouldBe 40_000
    }

    @Test
    fun `the minimum payment is floored at five pounds when two-and-a-half percent is smaller`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -10_000, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 500
    }

    @Test
    fun `the minimum payment is two-and-a-half percent of the owed amount above the floor`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -40_000, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 1_000
    }

    @Test
    fun `two-and-a-half percent rounds to the nearest penny, half up`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -33_333, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 833
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -33_340, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 834
    }

    @Test
    fun `at the crossover the floor and the percentage coincide at five pounds`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -20_000, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 500
    }

    @Test
    fun `just below the crossover the floor wins, just above it the percentage wins`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -19_600, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 500
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -20_400, cycleMonth = anyCycle)
            .minimumPaymentMinorUnits shouldBe 510
    }

    @Test
    fun `nothing owed means a zero statement balance and a zero minimum payment`() {
        val figures = StatementCalculator.figuresFor(closingBalanceMinorUnits = 0, cycleMonth = anyCycle)
        figures.statementBalanceMinorUnits shouldBe 0
        figures.minimumPaymentMinorUnits shouldBe 0
    }

    @Test
    fun `a credit balance from overpayment owes nothing and has a zero minimum payment`() {
        val figures = StatementCalculator.figuresFor(closingBalanceMinorUnits = 5_000, cycleMonth = anyCycle)
        figures.statementBalanceMinorUnits shouldBe -5_000
        figures.minimumPaymentMinorUnits shouldBe 0
    }

    @Test
    fun `the due date is the twenty-first of the month following the cycle`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -10_000, cycleMonth = YearMonth.of(2026, 1))
            .dueDate shouldBe LocalDate.of(2026, 2, 21)
    }

    @Test
    fun `the due date follows a short February into March`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -10_000, cycleMonth = YearMonth.of(2026, 2))
            .dueDate shouldBe LocalDate.of(2026, 3, 21)
    }

    @Test
    fun `the due date rolls across the year end from December into January`() {
        StatementCalculator.figuresFor(closingBalanceMinorUnits = -10_000, cycleMonth = YearMonth.of(2026, 12))
            .dueDate shouldBe LocalDate.of(2027, 1, 21)
    }
}
