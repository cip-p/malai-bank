package com.malai.ledger.entity

import com.malai.commons.model.Currency
import com.malai.ledger.model.LedgerAccountKind
import com.malai.ledger.model.LedgerGuardPolicy
import com.malai.ledger.model.LedgerGuardPolicy.ALLOW_NEGATIVE
import com.malai.ledger.model.LedgerGuardPolicy.FLOORED
import com.malai.ledger.model.LedgerGuardPolicy.NO_OVERDRAFT
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "ledger_accounts")
data class LedgerAccountEntity(

    @Id
    @Column(name = "account_id", nullable = false)
    val accountId: UUID,

    @Enumerated(EnumType.STRING)
    @Column(name = "currency", nullable = false, length = 10)
    val currency: Currency,

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    val kind: LedgerAccountKind,

    @Enumerated(EnumType.STRING)
    @Column(name = "guard_policy", nullable = false, length = 20)
    val guardPolicy: LedgerGuardPolicy,

    @Column(name = "floor_minor_units")
    val floorMinorUnits: Long? = null,
) {

    /**
     * The permitted **Available balance** minimum this account's funds guard enforces, or `null`
     * when the account is unbounded. `NO_OVERDRAFT` floors at zero, `ALLOW_NEGATIVE` is unbounded,
     * and `FLOORED` floors at the registered per-account minimum (the credit-card shape, −creditLimit).
     * A **Posting** or **Hold** is rejected when it would take Available below this figure.
     */
    val guardFloorMinorUnits: Long?
        get() = when (guardPolicy) {
            NO_OVERDRAFT -> 0L
            ALLOW_NEGATIVE -> null
            FLOORED -> floorMinorUnits
        }
}
