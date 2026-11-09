package com.malai.accountholder.controller

import com.malai.accountholder.model.*
import com.malai.accountholder.service.AccountHolderService
import com.malai.security.MalaiUserForUserIdOnly
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/account-holder")
class AccountHolderController(private val accountHolderService: AccountHolderService) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getAccountHolder(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): AccountHolderResponse {
        return accountHolderService.getAccountHolder(user.accountHolderId)
    }

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun createAccountHolder(
        @RequestBody request: CreateAccountHolderRequest,
        @AuthenticationPrincipal user: MalaiUserForUserIdOnly
    ): AccountHolderResponse {
        return accountHolderService.createAccountHolder(user.userId, request)
    }

    @GetMapping("/all", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getAllAccountHolders(@AuthenticationPrincipal user: MalaiUserForUserIdOnly): AccountHoldersResponse {
        return accountHolderService.getAllAccountHolders(user.userId)
    }

    @GetMapping("/name", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getAccountHolderName(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): AccountHolderNameResponse {
        return accountHolderService.getAccountHolderName(user.accountHolderId)
    }

    @GetMapping("/individual", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getIndividual(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): IndividualResponse {
        return accountHolderService.getIndividual(user.accountHolderId)
    }

    @PostMapping("/individual", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun createIndividual(
        @RequestBody request: CreateIndividualRequest,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): IndividualResponse {
        return accountHolderService.createIndividual(user.accountHolderId, request)
    }

    @PutMapping("/individual/email", consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun updateEmail(
        @RequestBody request: UpdateEmailRequest,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ) {
        accountHolderService.updateEmail(user.accountHolderId, request)
    }

    @GetMapping("/joint", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getJointAccount(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): JointAccountResponse {
        return accountHolderService.getJointAccount(user.accountHolderId)
    }

    @GetMapping("/business", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getBusiness(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): BusinessResponse {
        return accountHolderService.getBusiness(user.accountHolderId)
    }

    @GetMapping("/business/correspondence-address", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getBusinessCorrespondenceAddress(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): AddressResponse {
        return accountHolderService.getBusinessCorrespondenceAddress(user.accountHolderId)
    }

    @GetMapping("/business/registered-address", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getBusinessRegisteredAddress(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): AddressResponse {
        return accountHolderService.getBusinessRegisteredAddress(user.accountHolderId)
    }

    @GetMapping("/sole-trader", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getSoleTrader(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): SoleTraderResponse {
        return accountHolderService.getSoleTrader(user.accountHolderId)
    }

    @GetMapping("/profile-image")
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getProfileImage(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): ResponseEntity<ByteArray> {
        val profileImage = accountHolderService.getProfileImage(user.accountHolderId)
        return ResponseEntity.ok()
            .header(CONTENT_TYPE, profileImage.contentType)
            .body(profileImage.imageData)
    }

    @PutMapping("/profile-image")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun updateProfileImage(
        @RequestHeader("Content-Type") contentType: String,
        @RequestBody imageData: ByteArray,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ) {
        accountHolderService.updateProfileImage(user.accountHolderId, contentType, imageData)
    }

    @DeleteMapping("/profile-image")
    @PreAuthorize("hasRole('CUSTOMER')")
    fun deleteProfileImage(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId) {
        accountHolderService.deleteProfileImage(user.accountHolderId)
    }
}
