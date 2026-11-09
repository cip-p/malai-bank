package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class EntriesResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountId: UUID
    private var entries: List<EntryBuilder> = emptyList()
    private var openingBalanceMinorUnits: Long? = null
    private var closingBalanceMinorUnits: Long? = null

    fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
    fun withEntries(vararg entries: EntryBuilder) = apply { this.entries = entries.toList() }
    fun withOpeningBalanceMinorUnits(value: Long) = apply { this.openingBalanceMinorUnits = value }
    fun withClosingBalanceMinorUnits(value: Long) = apply { this.closingBalanceMinorUnits = value }

    override fun build(): String = JsonSerialiser.toJson(
        Entries(accountId, entries.map { it.build() }, openingBalanceMinorUnits, closingBalanceMinorUnits)
    )

    data class Entries(
        val accountId: UUID,
        val entries: List<EntryBuilder.Entry>,
        val openingBalanceMinorUnits: Long?,
        val closingBalanceMinorUnits: Long?,
    )

    companion object {
        fun anEntriesResponse() = EntriesResponseBuilder()
            .withAccountId(UUID.randomUUID())
    }

    class EntryBuilder private constructor() {
        private lateinit var entryId: UUID
        private lateinit var transactionId: UUID
        private lateinit var postedAt: String
        private var amountMinorUnits: Long = 0
        private var runningBalanceMinorUnits: Long = 0
        private var description: String? = null
        private var counterpartyId: UUID? = null
        private var merchantName: String? = null
        private var category: String? = null
        private var transactionType: String? = null

        fun withEntryId(entryId: UUID) = apply { this.entryId = entryId }
        fun withTransactionId(transactionId: UUID) = apply { this.transactionId = transactionId }
        fun withPostedAt(postedAt: String) = apply { this.postedAt = postedAt }
        fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
        fun withRunningBalanceMinorUnits(runningBalanceMinorUnits: Long) = apply { this.runningBalanceMinorUnits = runningBalanceMinorUnits }
        fun withDescription(description: String?) = apply { this.description = description }
        fun withCounterpartyId(counterpartyId: UUID?) = apply { this.counterpartyId = counterpartyId }
        fun withMerchantName(merchantName: String?) = apply { this.merchantName = merchantName }
        fun withCategory(category: String?) = apply { this.category = category }
        fun withTransactionType(transactionType: String?) = apply { this.transactionType = transactionType }

        fun build() = Entry(
            entryId,
            transactionId,
            postedAt,
            amountMinorUnits,
            runningBalanceMinorUnits,
            description,
            counterpartyId,
            merchantName,
            category,
            transactionType,
        )

        data class Entry(
            val entryId: UUID,
            val transactionId: UUID,
            val postedAt: String,
            val amountMinorUnits: Long,
            val runningBalanceMinorUnits: Long,
            val description: String?,
            val counterpartyId: UUID?,
            val merchantName: String?,
            val category: String?,
            val transactionType: String?,
        )

        companion object {
            fun anEntry() = EntryBuilder()
        }
    }
}
