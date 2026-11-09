package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class TransactionEntriesResponseBuilder private constructor() : TestBuilder {

    private lateinit var transactionId: UUID
    private lateinit var postedAt: String
    private lateinit var type: String
    private var description: String? = null
    private var entries: List<TransactionEntryBuilder> = emptyList()

    fun withTransactionId(transactionId: UUID) = apply { this.transactionId = transactionId }
    fun withPostedAt(postedAt: String) = apply { this.postedAt = postedAt }
    fun withType(type: String) = apply { this.type = type }
    fun withDescription(description: String?) = apply { this.description = description }
    fun withEntries(vararg entries: TransactionEntryBuilder) = apply { this.entries = entries.toList() }

    override fun build(): String = JsonSerialiser.toJson(buildModel())

    fun buildModel() =
        TransactionEntries(transactionId, postedAt, type, description, entries.map { it.build() })

    data class TransactionEntries(
        val transactionId: UUID,
        val postedAt: String,
        val type: String,
        val description: String?,
        val entries: List<TransactionEntryBuilder.TransactionEntry>,
    )

    companion object {
        fun aTransactionEntriesResponse() = TransactionEntriesResponseBuilder()
            .withTransactionId(UUID.randomUUID())
            .withPostedAt("2026-05-03T12:00:00Z")
            .withType("PAYMENT")
    }

    class TransactionEntryBuilder private constructor() {

        private lateinit var entryId: UUID
        private lateinit var accountId: UUID
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
        fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
        fun withTransactionId(transactionId: UUID) = apply { this.transactionId = transactionId }
        fun withPostedAt(postedAt: String) = apply { this.postedAt = postedAt }
        fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }
        fun withRunningBalanceMinorUnits(runningBalanceMinorUnits: Long) =
            apply { this.runningBalanceMinorUnits = runningBalanceMinorUnits }
        fun withDescription(description: String?) = apply { this.description = description }
        fun withCounterpartyId(counterpartyId: UUID?) = apply { this.counterpartyId = counterpartyId }
        fun withMerchantName(merchantName: String?) = apply { this.merchantName = merchantName }
        fun withCategory(category: String?) = apply { this.category = category }
        fun withTransactionType(transactionType: String?) = apply { this.transactionType = transactionType }

        fun build() = TransactionEntry(
            entryId, accountId, transactionId, postedAt, amountMinorUnits, runningBalanceMinorUnits,
            description, counterpartyId, merchantName, category, transactionType,
        )

        data class TransactionEntry(
            val entryId: UUID,
            val accountId: UUID,
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
            fun aTransactionEntry() = TransactionEntryBuilder()
        }
    }
}
