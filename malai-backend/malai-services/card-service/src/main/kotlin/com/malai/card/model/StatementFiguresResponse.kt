package com.malai.card.model

import java.time.LocalDate

data class StatementFiguresResponse(
    val statementBalanceMinorUnits: Long,
    val minimumPaymentMinorUnits: Long,
    val dueDate: LocalDate,
)
