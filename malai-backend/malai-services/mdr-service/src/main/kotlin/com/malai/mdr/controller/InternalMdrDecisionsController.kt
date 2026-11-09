package com.malai.mdr.controller

import com.malai.mdr.model.MdrActionableOutputResponse
import com.malai.mdr.model.MdrDecisionRecordResponse
import com.malai.mdr.model.RecordModelDecisionRequest
import com.malai.mdr.model.RecordModelDecisionResponse
import com.malai.mdr.service.MdrDecisionService
import com.malai.mdr.service.MdrReadService
import com.malai.security.InternalEndpoint
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val ONBOARDING_WRITE = "hasAuthority('SCOPE_malai-internal/onboarding.write')"

// Service-to-service surface for Decisions. Three routes:
//   POST /                                -- append a Model Decision to the chain.
//   GET  /{decisionId}                    -- the record read: the Decision as recorded plus the
//                                            Ruling in force, for a consumer assembling a view of
//                                            its own Decisions. Never the Disposition thread and
//                                            never the Raw output -- displaying a ruling is not
//                                            acting on one, and acting stays the gate's door.
//   GET  /{decisionId}/actionable-output  -- the gate, the same one the Ops surface reads through.
// The gate is the MDR's single enforcement surface (ADR-0009): consumers never read Human
// Dispositions and never re-derive what "actionable" means, so this route is a second *door* onto
// MdrReadService.actionableOutput, not a second implementation. Its semantics are therefore the
// admin gate's, exactly: ACCEPT serves the Raw output, EDIT serves the stored edited output, and
// REJECT or no ruling is 409 -- which a consumer reads as "leave it parked".
//
// The scope is the shared malai-internal/onboarding.write rather than a read-only one because
// SecurityConfig.internalServicesFilterChain admits exactly that scope on every internal path; a
// narrower scope would be a Cognito resource-server change, not a controller change.
@RestController
@RequestMapping("/api/v1/internal/mdr/decisions")
class InternalMdrDecisionsController(
    private val service: MdrDecisionService,
    private val readService: MdrReadService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(CREATED)
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun recordDecision(
        @Valid @RequestBody request: RecordModelDecisionRequest,
    ): RecordModelDecisionResponse {
        log.debug("internal: record model decision {} outcome={}", request.decisionId, request.outcome)
        return service.recordDecision(request)
    }

    @GetMapping("/{decisionId}", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun decisionRecord(@PathVariable decisionId: UUID): MdrDecisionRecordResponse {
        log.debug("internal: decision record for decision {}", decisionId)
        return readService.decisionRecord(decisionId)
    }

    @GetMapping("/{decisionId}/actionable-output", produces = [APPLICATION_JSON_VALUE])
    @InternalEndpoint
    @PreAuthorize(ONBOARDING_WRITE)
    fun actionableOutput(@PathVariable decisionId: UUID): MdrActionableOutputResponse {
        log.debug("internal: actionable-output for decision {}", decisionId)
        return readService.actionableOutput(decisionId)
    }
}
