package com.malai.ledger.controller

import com.malai.ledger.model.RegisterLedgerAccountRequest
import com.malai.ledger.model.RegisterLedgerAccountResponse
import com.malai.ledger.service.LedgerAccountRegistry
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/internal/ledger/accounts")
class LedgerAccountsController(private val registry: LedgerAccountRegistry) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun register(@Valid @RequestBody request: RegisterLedgerAccountRequest): RegisterLedgerAccountResponse {
        val account = registry.register(request)
        return RegisterLedgerAccountResponse(
            accountId = account.accountId,
            currency = account.currency.name,
            kind = account.kind,
            guardPolicy = account.guardPolicy,
            floorMinorUnits = account.floorMinorUnits,
        )
    }
}
