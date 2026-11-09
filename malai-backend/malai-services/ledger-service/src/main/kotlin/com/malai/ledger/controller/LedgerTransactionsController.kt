package com.malai.ledger.controller

import com.malai.ledger.model.PostTransactionRequest
import com.malai.ledger.model.PostTransactionResponse
import com.malai.ledger.model.TracedTransactionsResponse
import com.malai.ledger.model.TransactionEntriesResponse
import com.malai.ledger.service.EntriesService
import com.malai.ledger.service.PostingEngine
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/ledger/transactions")
class LedgerTransactionsController(
    private val postingEngine: PostingEngine,
    private val entriesService: EntriesService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun post(@Valid @RequestBody request: PostTransactionRequest): PostTransactionResponse =
        postingEngine.post(request)

    @GetMapping("/{transactionId}/entries", produces = [APPLICATION_JSON_VALUE])
    fun entries(@PathVariable transactionId: UUID): TransactionEntriesResponse =
        entriesService.listTransactionEntries(transactionId)

    @GetMapping(produces = [APPLICATION_JSON_VALUE], params = ["traceId"])
    fun forTrace(@RequestParam traceId: String): TracedTransactionsResponse =
        entriesService.listTransactionsForTrace(traceId)
}
