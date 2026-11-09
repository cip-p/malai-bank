package com.malai.card.controller

import com.malai.card.model.CardReplacementResponse
import com.malai.card.model.CardResponse
import com.malai.card.service.CardLifecycleService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/cards")
class CardLifecycleController(private val cardLifecycleService: CardLifecycleService) {

    @PostMapping("/{cardId}/freeze", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun freeze(
        @PathVariable cardId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CardResponse = cardLifecycleService.freeze(user, cardId)

    @PostMapping("/{cardId}/unfreeze", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun unfreeze(
        @PathVariable cardId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CardResponse = cardLifecycleService.unfreeze(user, cardId)

    @PostMapping("/{cardId}/report-lost-or-stolen", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun reportLostOrStolen(
        @PathVariable cardId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CardReplacementResponse = cardLifecycleService.reportLostOrStolen(user, cardId)
}
