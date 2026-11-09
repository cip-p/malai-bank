package com.malai.security.audit

// Audit actor classification (D-2.11). Mapped to the audit_events.actor_type VARCHAR(20)
// column at write time and back to the enum at verify time.
enum class ActorType {
    CUSTOMER,
    SUPPORT,
    ADMIN,
    SYSTEM,
}
