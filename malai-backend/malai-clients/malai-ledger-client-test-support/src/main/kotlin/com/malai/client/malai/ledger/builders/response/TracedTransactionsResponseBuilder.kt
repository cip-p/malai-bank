package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class TracedTransactionsResponseBuilder private constructor() : TestBuilder {

    private lateinit var traceId: String
    private var transactions: List<TransactionEntriesResponseBuilder> = emptyList()

    fun withTraceId(traceId: String) = apply { this.traceId = traceId }
    fun withTransactions(vararg transactions: TransactionEntriesResponseBuilder) =
        apply { this.transactions = transactions.toList() }

    override fun build(): String = JsonSerialiser.toJson(
        TracedTransactions(traceId, transactions.map { it.buildModel() })
    )

    data class TracedTransactions(
        val traceId: String,
        val transactions: List<TransactionEntriesResponseBuilder.TransactionEntries>,
    )

    companion object {
        fun aTracedTransactionsResponse() = TracedTransactionsResponseBuilder()
            .withTraceId("some-trace-id")
    }
}
