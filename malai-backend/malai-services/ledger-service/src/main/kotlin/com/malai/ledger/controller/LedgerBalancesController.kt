package com.malai.ledger.controller

import com.malai.ledger.model.BalancesResponse
import com.malai.ledger.service.BalanceService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/ledger/accounts")
class LedgerBalancesController(private val balanceService: BalanceService) {

    @GetMapping("/{accountId}/balances", produces = [APPLICATION_JSON_VALUE])
    fun balances(@PathVariable accountId: UUID): BalancesResponse =
        balanceService.getBalances(accountId)
}
