package com.malai.mdr.model

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import java.util.UUID

// The three rulings a human may append to a Model Decision. ACCEPT and REJECT carry the ruling
// alone; EDIT carries the full edited output that becomes the hash-covered actionable artifact.
enum class HumanDispositionKind { ACCEPT, EDIT, REJECT }

// Request body for POST /api/v1/admin/mdr/decisions/{decisionId}/dispositions. The ruling human's
// id is NOT here -- it comes from the authenticated principal, never the payload, so a caller can
// never forge whose ruling this is. The decision being ruled on comes from the path, so the parent
// link is derived, not supplied. editedOutput is present iff EDIT (validated below and by the DB
// CHECK backstop), redacted before it is hashed and persisted.
data class RecordHumanDispositionRequest(
    @field:NotNull val entryId: UUID?,
    @field:NotNull val disposition: HumanDispositionKind?,
    val editedOutput: String? = null,
) {
    @get:AssertTrue(message = "editedOutput must be present iff disposition is EDIT")
    val editedOutputMatchesDisposition: Boolean
        get() = when (disposition) {
            HumanDispositionKind.EDIT -> !editedOutput.isNullOrBlank()
            HumanDispositionKind.ACCEPT, HumanDispositionKind.REJECT -> editedOutput == null
            null -> true
        }
}

data class RecordHumanDispositionResponse(
    val entryId: UUID,
    val decisionId: UUID,
    val parentEntryId: UUID,
    val seq: Long,
    val disposition: String,
)

// Flat response shape for GET /api/v1/admin/mdr/chain-verification, identical in shape to the
// audit plane's AuditChainVerificationResponse. Mirrors the sealed VerificationResult in
// malai-security via a `result` discriminator plus the only two fields any concrete result needs
// (`rowsVerified` for Ok, `atSeq` for the three positional failures). SOC / audit tooling can
// SELECT on `result` without parsing a tagged union.
data class MdrChainVerificationResponse(
    val result: MdrChainVerificationResult,
    val rowsVerified: Long? = null,
    val atSeq: Long? = null,
)

enum class MdrChainVerificationResult {
    OK,
    GAP_AT_SEQ,
    PREV_HASH_MISMATCH,
    CURRENT_HASH_MISMATCH,
    HEAD_MISMATCH,
}
