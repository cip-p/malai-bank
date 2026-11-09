package com.malai.client.malai.stub.directory.builders.response

import kotlin.properties.Delegates

class CounterpartyResponseBuilder private constructor() {

    private var sortCode: String
    private var accountNumber: String
    private var bank: String
    private var nameOnRecord: String
    private var outboundOutcome: String
    private var returnReason: String? = null
    private var includeReturnReason by Delegates.notNull<Boolean>()

    init {
        includeReturnReason = false
        outboundOutcome = "SETTLES"
        bank = "NatWest"
        nameOnRecord = "Alice Walker"
        sortCode = "60-00-01"
        accountNumber = "12345678"
    }

    fun withSortCode(sortCode: String) = apply { this.sortCode = sortCode }
    fun withAccountNumber(accountNumber: String) = apply { this.accountNumber = accountNumber }
    fun withBank(bank: String) = apply { this.bank = bank }
    fun withNameOnRecord(nameOnRecord: String) = apply { this.nameOnRecord = nameOnRecord }
    fun withOutboundOutcome(outboundOutcome: String) = apply { this.outboundOutcome = outboundOutcome }
    fun withReturnReason(returnReason: String) = apply {
        this.returnReason = returnReason
        this.includeReturnReason = true
    }

    fun toMap(): Map<String, Any?> {
        val base = linkedMapOf<String, Any?>(
            "sortCode" to sortCode,
            "accountNumber" to accountNumber,
            "bank" to bank,
            "nameOnRecord" to nameOnRecord,
            "outboundOutcome" to outboundOutcome,
        )
        if (includeReturnReason) {
            base["returnReason"] = returnReason
        }
        return base
    }

    companion object {
        fun aCounterpartyResponse() = CounterpartyResponseBuilder()
    }
}
