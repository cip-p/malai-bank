package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import kotlin.properties.Delegates

class StatementFiguresResponseBuilder private constructor() : TestBuilder {

    private var statementBalanceMinorUnits: Long by Delegates.notNull()
    private var minimumPaymentMinorUnits: Long by Delegates.notNull()
    private lateinit var dueDate: String

    fun withStatementBalanceMinorUnits(statementBalanceMinorUnits: Long) =
        apply { this.statementBalanceMinorUnits = statementBalanceMinorUnits }

    fun withMinimumPaymentMinorUnits(minimumPaymentMinorUnits: Long) =
        apply { this.minimumPaymentMinorUnits = minimumPaymentMinorUnits }

    fun withDueDate(dueDate: String) = apply { this.dueDate = dueDate }

    fun toModel() = StatementFigures(statementBalanceMinorUnits, minimumPaymentMinorUnits, dueDate)

    override fun build(): String = JsonSerialiser.toJson(toModel())

    data class StatementFigures(
        val statementBalanceMinorUnits: Long,
        val minimumPaymentMinorUnits: Long,
        val dueDate: String,
    )

    companion object {
        fun statementFigures() = StatementFiguresResponseBuilder()
            .withStatementBalanceMinorUnits(40_000)
            .withMinimumPaymentMinorUnits(1_000)
            .withDueDate("2026-06-21")
    }
}
