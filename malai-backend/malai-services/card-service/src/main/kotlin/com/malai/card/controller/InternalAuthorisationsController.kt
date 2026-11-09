package com.malai.card.controller

import com.malai.card.model.AccountAuthorisationsResponse
import com.malai.card.model.AuthoriseCardRequest
import com.malai.card.model.AuthoriseCardResponse
import com.malai.card.model.InternalAuthorisationResponse
import com.malai.card.service.AccountAuthorisationsService
import com.malai.card.service.CardAuthorisationService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/cards/authorisations")
class InternalAuthorisationsController(
    private val cardAuthorisationService: CardAuthorisationService,
    private val accountAuthorisationsService: AccountAuthorisationsService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun authorise(@RequestBody request: AuthoriseCardRequest): AuthoriseCardResponse =
        cardAuthorisationService.authorise(request)

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    fun listForAccount(@RequestParam accountId: UUID): AccountAuthorisationsResponse =
        accountAuthorisationsService.listForAccount(accountId)

    @GetMapping("/{authorisationId}", produces = [APPLICATION_JSON_VALUE])
    fun resolve(
        @PathVariable authorisationId: UUID,
        @RequestParam accountHolderId: UUID,
    ): InternalAuthorisationResponse =
        accountAuthorisationsService.resolveForHolder(authorisationId, accountHolderId)
}
