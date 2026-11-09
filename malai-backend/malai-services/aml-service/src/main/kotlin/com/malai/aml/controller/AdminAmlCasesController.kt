package com.malai.aml.controller

import com.malai.aml.model.AmlCaseDetailResponse
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.model.AmlCaseSummariesResponse
import com.malai.aml.model.AmlSarDraftResponse
import com.malai.aml.service.AmlCaseReadService
import com.malai.aml.service.AmlCaseService
import com.malai.aml.service.SarDraftService
import com.malai.security.MalaiUserForUserIdOnly
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/admin/aml/cases")
class AdminAmlCasesController(
    private val readService: AmlCaseReadService,
    private val caseService: AmlCaseService,
    private val sarDraftService: SarDraftService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun listCases(@RequestParam(required = false) status: AmlCaseStatus?): AmlCaseSummariesResponse {
        log.debug("admin: aml case worklist status={}", status)
        return readService.listCases(status)
    }

    @GetMapping("/{caseId}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun caseDetail(@PathVariable caseId: UUID): AmlCaseDetailResponse {
        log.debug("admin: aml case detail {}", caseId)
        return readService.caseDetail(caseId)
    }

    @PostMapping("/{caseId}/sar-draft", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(CREATED)
    fun requestSarDraft(@PathVariable caseId: UUID): AmlSarDraftResponse {
        log.debug("admin: sar draft requested for aml case {}", caseId)
        return sarDraftService.requestDraft(caseId)
    }

    @PostMapping("/{caseId}/close-dismissed", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun closeDismissed(
        @PathVariable caseId: UUID,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ): AmlCaseDetailResponse {
        log.debug("admin: closing aml case {} as dismissed", caseId)
        caseService.closeDismissed(caseId, user.userId)
        return readService.caseDetail(caseId)
    }

    @PostMapping("/{caseId}/close-reported", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    fun closeReported(
        @PathVariable caseId: UUID,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly,
    ): AmlCaseDetailResponse {
        log.debug("admin: closing aml case {} as reported", caseId)
        caseService.closeReported(caseId, user.userId)
        return readService.caseDetail(caseId)
    }
}
