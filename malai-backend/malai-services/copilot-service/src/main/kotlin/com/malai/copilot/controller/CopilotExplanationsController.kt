package com.malai.copilot.controller

import com.malai.copilot.model.ExplanationRequest
import com.malai.copilot.model.ExplanationResponse
import com.malai.copilot.model.SubjectType
import com.malai.copilot.service.ExplanationService
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin/copilot")
class CopilotExplanationsController(
    private val service: ExplanationService,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    @PostMapping("/explanations", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun explain(@Valid @RequestBody request: ExplanationRequest): ExplanationResponse {
        log.debug("copilot: explanation requested for {} {}", request.subjectType, request.subjectId)
        return when (request.subjectType!!) {
            SubjectType.PAYMENT -> service.explainPayment(request.subjectId!!)
        }
    }
}
