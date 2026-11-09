package com.malai.ledger.model

enum class LedgerAccountKind { CUSTOMER, SYSTEM }

enum class LedgerGuardPolicy { NO_OVERDRAFT, ALLOW_NEGATIVE, FLOORED }

enum class HoldStatus { PLACED, SETTLED, RELEASED }
