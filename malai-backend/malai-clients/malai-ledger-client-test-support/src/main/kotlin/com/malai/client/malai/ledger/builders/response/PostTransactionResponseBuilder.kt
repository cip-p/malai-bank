package com.malai.client.malai.ledger.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class PostTransactionResponseBuilder private constructor() : TestBuilder {

    private lateinit var transactionId: UUID
    private lateinit var postedAt: String
    private var entries: List<PostedEntryBuilder> = emptyList()

    fun withTransactionId(transactionId: UUID) = apply { this.transactionId = transactionId }
    fun withPostedAt(postedAt: String) = apply { this.postedAt = postedAt }
    fun withEntries(vararg entries: PostedEntryBuilder) = apply { this.entries = entries.toList() }

    override fun build(): String = JsonSerialiser.toJson(toModel())

    fun toModel(): Transaction = Transaction(transactionId, postedAt, entries.map { it.build() })

    data class Transaction(
        val transactionId: UUID,
        val postedAt: String,
        val entries: List<PostedEntryBuilder.Entry>,
    )

    companion object {
        fun aPostTransactionResponse() = PostTransactionResponseBuilder()
    }

    class PostedEntryBuilder private constructor() {
        private lateinit var entryId: UUID
        private lateinit var accountId: UUID
        private var amountMinorUnits: Long = 0

        fun withEntryId(entryId: UUID) = apply { this.entryId = entryId }
        fun withAccountId(accountId: UUID) = apply { this.accountId = accountId }
        fun withAmountMinorUnits(amountMinorUnits: Long) = apply { this.amountMinorUnits = amountMinorUnits }

        fun build() = Entry(entryId, accountId, amountMinorUnits)

        data class Entry(val entryId: UUID, val accountId: UUID, val amountMinorUnits: Long)

        companion object {
            fun aPostedEntry() = PostedEntryBuilder()
        }
    }
}
