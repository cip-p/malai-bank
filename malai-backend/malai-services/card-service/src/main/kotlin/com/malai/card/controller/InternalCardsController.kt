package com.malai.card.controller

import com.malai.card.model.InternalCardResponse
import com.malai.card.model.IssueCardRequest
import com.malai.card.model.IssueCardResponse
import com.malai.card.service.CardIssuer
import com.malai.card.service.CardsService
import com.malai.card.service.toIssueResponse
import jakarta.validation.Valid
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
@RequestMapping("/api/v1/internal/cards")
class InternalCardsController(
    private val cardIssuer: CardIssuer,
    private val cardsService: CardsService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun issue(@Valid @RequestBody request: IssueCardRequest): IssueCardResponse =
        cardIssuer.issue(request).toIssueResponse()

    @GetMapping("/{cardId}", produces = [APPLICATION_JSON_VALUE])
    fun resolve(
        @PathVariable cardId: UUID,
        @RequestParam accountHolderId: UUID,
    ): InternalCardResponse =
        cardsService.resolveForHolder(cardId, accountHolderId)
}
