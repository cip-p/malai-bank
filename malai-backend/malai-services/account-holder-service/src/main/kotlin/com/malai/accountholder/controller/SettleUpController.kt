package com.malai.accountholder.controller

import com.malai.accountholder.model.SettleUpProfileResponse
import com.malai.accountholder.service.AccountHolderService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/settle-up")
class SettleUpController(private val accountHolderService: AccountHolderService) {

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getSettleUpProfile(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): SettleUpProfileResponse {
        return accountHolderService.getSettleUpProfile(user.accountHolderId)
    }
}
