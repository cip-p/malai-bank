package com.malai.sandbox.controller

import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.card.model.PresentNowResult
import com.malai.security.MalaiUser
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sandbox/card-presentments")
class SandboxCardPresentmentsController(
    private val stubClient: MalaiStubClient,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun trigger(
        @Suppress("UNUSED_PARAMETER") @AuthenticationPrincipal user: MalaiUser,
    ): PresentNowResult =
        stubClient.presentPendingCards()
}
