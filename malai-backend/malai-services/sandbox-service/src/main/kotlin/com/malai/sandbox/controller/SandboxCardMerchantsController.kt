package com.malai.sandbox.controller

import com.malai.client.malai.stub.MalaiStubClient
import com.malai.client.malai.stub.card.model.CardMerchantsResponse
import com.malai.security.MalaiUser
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/sandbox/card-merchants")
class SandboxCardMerchantsController(
    private val stubClient: MalaiStubClient,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun list(
        @Suppress("UNUSED_PARAMETER") @AuthenticationPrincipal user: MalaiUser,
    ): CardMerchantsResponse =
        stubClient.getCardMerchants()
}
