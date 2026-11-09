package com.malai.mdr.controller

import com.malai.mdr.service.MdrChainVerifier
import com.malai.mdr.model.MdrChainVerificationResponse
import com.malai.mdr.model.MdrChainVerificationResult
import com.malai.security.audit.VerificationResult
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

// ADMIN-only operational endpoint mirroring the audit plane's AdminController: GET
// /api/v1/admin/mdr/chain-verification triggers [MdrChainVerifier] on demand over scope 'mdr'.
// The scheduled chain-verification job (hourly, every scope) stays the primary control; this
// endpoint is the on-demand answer for ops / SOC / audit. Read-only, so it does not reach the
// AuditWriter (the mutatingControllersReachAuditWriter rule gates only mutating endpoints).
@RestController
@RequestMapping("/api/v1/admin/mdr")
class AdminMdrChainController(
    private val verifier: MdrChainVerifier,
) {

    @GetMapping("/chain-verification", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun verifyMdrChain(): MdrChainVerificationResponse =
        when (val result = verifier.verify()) {
            is VerificationResult.Ok ->
                MdrChainVerificationResponse(
                    result = MdrChainVerificationResult.OK,
                    rowsVerified = result.rowsVerified,
                )
            is VerificationResult.GapAtSeq ->
                MdrChainVerificationResponse(
                    result = MdrChainVerificationResult.GAP_AT_SEQ,
                    atSeq = result.expected,
                )
            is VerificationResult.PrevHashMismatch ->
                MdrChainVerificationResponse(
                    result = MdrChainVerificationResult.PREV_HASH_MISMATCH,
                    atSeq = result.seq,
                )
            is VerificationResult.CurrentHashMismatch ->
                MdrChainVerificationResponse(
                    result = MdrChainVerificationResult.CURRENT_HASH_MISMATCH,
                    atSeq = result.seq,
                )
            is VerificationResult.HeadMismatch ->
                MdrChainVerificationResponse(
                    result = MdrChainVerificationResult.HEAD_MISMATCH,
                )
        }
}
