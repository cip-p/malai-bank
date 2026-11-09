package com.malai.stub.directory

import com.fasterxml.jackson.annotation.JsonInclude
import com.malai.payments.messages.ReturnReason
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/scenarios/counterparties")
class StubCounterpartiesController(private val directory: StubFictionalDirectory) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    fun list(): CounterpartiesResponse = CounterpartiesResponse(
        counterparties = directory.all().map {
            CounterpartyResponse(
                sortCode = it.sortCode,
                accountNumber = it.accountNumber,
                bank = it.bank,
                nameOnRecord = it.name,
                outboundOutcome = it.outboundOutcome,
                returnReason = it.returnReason,
            )
        }
    )
}

data class CounterpartiesResponse(val counterparties: List<CounterpartyResponse>)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class CounterpartyResponse(
    val sortCode: String,
    val accountNumber: String,
    val bank: String,
    val nameOnRecord: String,
    val outboundOutcome: OutboundOutcome,
    val returnReason: ReturnReason?,
)
