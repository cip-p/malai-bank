package com.malai.mdr.controller

import com.malai.mdr.model.MdrActionableOutputResponse
import com.malai.mdr.model.MdrDecisionSummariesResponse
import com.malai.mdr.model.MdrDecisionThreadResponse
import com.malai.mdr.model.MdrRulingState
import com.malai.mdr.service.MdrReadService
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// ADMIN-only read surface for Decisions. Three reads, all read-only (so exempt from
// mutatingControllersReachAuditWriter):
//   GET /                                -- the worklist: one summary per Decision, newest first,
//                                           optionally narrowed by ruling state and by whether a
//                                           ruling is expected at all (advisory, ADR-0013).
//   GET /{decisionId}                    -- the replay read: the full Entry thread in chain order,
//                                           with the EDIT diff derived at read time.
//   GET /{decisionId}/actionable-output  -- the gate: refuses (409) until the Ruling in force is
//                                           ACCEPT or EDIT, so a consumer structurally cannot act
//                                           on unreviewed or rejected model output.
// An unknown decisionId is a 404 on the by-id reads; an unknown rulingState is a 400.
@RestController
@RequestMapping("/api/v1/admin/mdr/decisions")
class AdminMdrDecisionsController(
    private val service: MdrReadService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun listDecisions(
        @RequestParam(required = false) rulingState: MdrRulingState?,
        @RequestParam(required = false) advisory: Boolean?,
    ): MdrDecisionSummariesResponse {
        log.debug("admin: decision worklist rulingState={} advisory={}", rulingState, advisory)
        return service.listDecisions(rulingState, advisory)
    }

    @GetMapping("/{decisionId}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun replayDecision(@PathVariable decisionId: UUID): MdrDecisionThreadResponse {
        log.debug("admin: replay decision {}", decisionId)
        return service.replay(decisionId)
    }

    @GetMapping("/{decisionId}/actionable-output", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun actionableOutput(@PathVariable decisionId: UUID): MdrActionableOutputResponse {
        log.debug("admin: actionable-output for decision {}", decisionId)
        return service.actionableOutput(decisionId)
    }
}
