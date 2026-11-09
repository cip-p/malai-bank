package com.malai.auth.controller

import com.malai.auth.model.AuditChainVerificationResponse
import com.malai.auth.model.AuditChainVerificationResult
import com.malai.security.audit.AuditChainVerifier
import com.malai.security.audit.VerificationResult
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Quick 260512-ttc — ADMIN-only operational endpoint. Closes Phase 2 ROADMAP
 * success criterion #5's vacuous-truth gap: prior to this quick task no
 * controller method gated on `hasRole('ADMIN')`, so the
 * "customer JWT receives 403 on any admin endpoint" half of SC#5 had no
 * structural counterpart to test.
 *
 * `GET /api/v1/admin/audit-chain/verification` triggers the existing
 * [com.malai.security.audit.AuditChainVerifier] (Plan 02-11 / D-2.11) on
 * demand. The scheduled `ChainVerificationJob` (Plan 02-11, hourly cron, now
 * every chain scope) stays the primary integrity check; this endpoint is for ops / SOC / audit
 * who need an on-demand answer (e.g. mid-incident, before signing off a
 * change window).
 */
@RestController
@RequestMapping("/api/v1/admin")
class AdminController(
    private val verifier: AuditChainVerifier,
) {

    @GetMapping("/audit-chain/verification", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun verifyAuditChain(): AuditChainVerificationResponse =
        when (val result = verifier.verify()) {
            is VerificationResult.Ok ->
                AuditChainVerificationResponse(
                    result = AuditChainVerificationResult.OK,
                    rowsVerified = result.rowsVerified,
                )
            is VerificationResult.GapAtSeq ->
                AuditChainVerificationResponse(
                    result = AuditChainVerificationResult.GAP_AT_SEQ,
                    atSeq = result.expected,
                )
            is VerificationResult.PrevHashMismatch ->
                AuditChainVerificationResponse(
                    result = AuditChainVerificationResult.PREV_HASH_MISMATCH,
                    atSeq = result.seq,
                )
            is VerificationResult.CurrentHashMismatch ->
                AuditChainVerificationResponse(
                    result = AuditChainVerificationResult.CURRENT_HASH_MISMATCH,
                    atSeq = result.seq,
                )
            is VerificationResult.HeadMismatch ->
                AuditChainVerificationResponse(
                    result = AuditChainVerificationResult.HEAD_MISMATCH,
                )
        }
}
