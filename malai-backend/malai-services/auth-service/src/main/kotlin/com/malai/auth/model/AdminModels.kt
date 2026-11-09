package com.malai.auth.model

// Flat response shape for GET /api/v1/admin/audit-chain/verification (Quick 260512-ttc).
// Mirrors the sealed `VerificationResult` in malai-security via a `result` discriminator
// plus the only two fields any concrete result needs (`rowsVerified` for Ok,
// `atSeq` for the three positional failures). The Ok / failure split is opaque
// to downstream tools — SOC / audit log can SELECT on `result` without parsing
// a tagged union.
data class AuditChainVerificationResponse(
    val result: AuditChainVerificationResult,
    val rowsVerified: Long? = null,
    val atSeq: Long? = null,
)

enum class AuditChainVerificationResult {
    OK,
    GAP_AT_SEQ,
    PREV_HASH_MISMATCH,
    CURRENT_HASH_MISMATCH,
    HEAD_MISMATCH,
}
