package com.malai.accounts.entity

import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import jakarta.persistence.*
import jakarta.persistence.EnumType.STRING
import java.time.Instant
import java.util.*

@Entity
@Table(name = "accounts")
data class AccountEntity(

    @Id
    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    val accountType: AccountType,

    val defaultCategory: UUID,

    @Enumerated(EnumType.STRING)
    val currency: Currency,

    val createdAt: Instant,

    val name: String,

    val userId: UUID,

    val accountHolderId: UUID,

    @Enumerated(STRING)
    val status: OnboardingStatus,
)

enum class AccountType {
    PRIMARY,
    ADDITIONAL,
    LOAN,
    FIXED_TERM_DEPOSIT,
    SAVINGS,

    /**
     * Issue 08 of cards: the credit card account, created only by the Credit
     * card application (never at registration). Ledger-backed like any account
     * but floored at the negated credit limit; excluded from Confirmation of
     * Payee. Carries ordinary sort-code/account-number identifiers so
     * repayments ride the existing book-transfer path.
     */
    CREDIT_CARD,

    /**
     * Issue 02 of send-and-receive-money: per-rail suspense + scheme-settlement
     * accounts owned by the System user. Hold the bank's notional position
     * while a payment is in-flight. Filtered out of GET /api/v1/accounts so
     * customers never see them.
     */
    SUSPENSE,
}
