package com.malai.accountholder.controller

import com.malai.accountholder.model.AddressUpdateRequest
import com.malai.accountholder.model.AddressesResponse
import com.malai.accountholder.service.AccountHolderService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/addresses")
class AddressController(private val accountHolderService: AccountHolderService) {

    @GetMapping
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getAddresses(
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): AddressesResponse {
        return accountHolderService.getAddresses(user.accountHolderId)
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    fun updateAddress(
        @RequestBody request: AddressUpdateRequest,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ) {
        accountHolderService.updateAddress(user.accountHolderId, request)
    }
}
