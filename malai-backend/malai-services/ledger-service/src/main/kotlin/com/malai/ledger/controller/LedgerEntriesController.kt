package com.malai.ledger.controller

import com.malai.ledger.model.EntriesResponse
import com.malai.ledger.service.EntriesService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/ledger/accounts")
class LedgerEntriesController(private val entriesService: EntriesService) {

    @GetMapping("/{accountId}/entries", produces = [APPLICATION_JSON_VALUE])
    fun entries(
        @PathVariable accountId: UUID,
        @RequestParam(required = false) periodStart: String?,
        @RequestParam(required = false) periodEnd: String?,
        @RequestParam(required = false, defaultValue = "100") limit: Int,
    ): EntriesResponse = entriesService.listEntries(accountId, periodStart, periodEnd, limit)
}
