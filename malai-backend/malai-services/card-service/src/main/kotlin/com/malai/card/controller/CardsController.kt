package com.malai.card.controller

import com.malai.card.model.CardDetailResponse
import com.malai.card.model.CardsResponse
import com.malai.card.service.CardsService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cards")
class CardsController(private val cardsService: CardsService) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun list(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CardsResponse = cardsService.list(user.accountHolderId)

    @GetMapping("/{cardId}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun detail(
        @PathVariable cardId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CardDetailResponse = cardsService.detail(cardId, user.accountHolderId)
}
