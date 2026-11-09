package com.malai.mdr.controller

import com.malai.mdr.model.RecordHumanDispositionRequest
import com.malai.mdr.model.RecordHumanDispositionResponse
import com.malai.mdr.service.MdrDispositionService
import com.malai.security.MalaiUserForUserIdOnly
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// ADMIN-only human write surface: a back-office reviewer appends a Human Disposition ruling on a
// prior Model Decision. The ruling human's id is taken from the authenticated principal, never the
// payload, so who ruled cannot be forged; the decision being ruled on comes from the path, so the
// parent link is derived, not supplied. A mutating endpoint, so it reaches the AuditWriter via
// MdrDispositionService (the mutatingControllersReachAuditWriter rule).
@RestController
@RequestMapping("/api/v1/admin/mdr/decisions")
class AdminMdrDispositionsController(
    private val service: MdrDispositionService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping("/{decisionId}/dispositions", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @ResponseStatus(CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun recordDisposition(
        @PathVariable decisionId: UUID,
        @Valid @RequestBody request: RecordHumanDispositionRequest,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ): RecordHumanDispositionResponse {
        log.debug("admin: record {} disposition on decision {}", request.disposition, decisionId)
        return service.recordDisposition(decisionId, user.userId, request)
    }
}
