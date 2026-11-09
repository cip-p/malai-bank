package com.malai.card.controller

import com.malai.card.model.StatementFiguresResponse
import com.malai.card.statement.StatementCalculator
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1/internal/cards/statement-figures")
class InternalStatementFiguresController {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    fun figuresFor(
        @RequestParam closingBalanceMinorUnits: Long,
        @RequestParam cycleMonth: String,
    ): StatementFiguresResponse {
        val figures = StatementCalculator.figuresFor(closingBalanceMinorUnits, YearMonth.parse(cycleMonth))
        return StatementFiguresResponse(
            statementBalanceMinorUnits = figures.statementBalanceMinorUnits,
            minimumPaymentMinorUnits = figures.minimumPaymentMinorUnits,
            dueDate = figures.dueDate,
        )
    }
}
