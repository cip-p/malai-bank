package com.malai.accounts.controller

import com.malai.accounts.model.*
import com.malai.accounts.service.AccountsService
import com.malai.security.MalaiUserWithAccountHolderId
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/v1/accounts")
class AccountsController(
    private val accountsService: AccountsService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getAccounts(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): AccountsResponse {
        return accountsService.getAccounts(user.accountHolderId)
    }

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.OK)
    fun createAccount(
        @RequestBody request: CreateAccountRequest,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): AccountResponse {
        return accountsService.createAccount(request, user.userId, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/balance", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getBalance(
        @PathVariable accountUid: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): BalanceResponse {
        return accountsService.getBalance(accountUid, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/identifiers", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getIdentifiers(
        @PathVariable accountUid: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): AccountIdentifiersResponse {
        return accountsService.getIdentifiers(accountUid, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/transactions", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getTransactions(
        @PathVariable accountUid: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): TransactionsResponse {
        return accountsService.getTransactions(accountUid, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/statements/{yyyymm:\\d{4}-(?:0[1-9]|1[0-2])}", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getStatement(
        @PathVariable accountUid: UUID,
        @PathVariable yyyymm: String,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): StatementResponse {
        return accountsService.getStatement(accountUid, user.accountHolderId, yyyymm)
    }

    @GetMapping("/{accountUid}/spending", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getSpending(
        @PathVariable accountUid: UUID,
        @RequestParam month: String,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): SpendingBreakdownResponse {
        return accountsService.getSpending(accountUid, user.accountHolderId, month)
    }

    @GetMapping("/{accountUid}/forecast", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getForecast(
        @PathVariable accountUid: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): BalanceForecastResponse {
        return accountsService.getForecast(accountUid, user.accountHolderId)
    }

    @PutMapping("/{accountUid}/transactions/{entryId}/category", consumes = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.OK)
    fun overrideCategory(
        @PathVariable accountUid: UUID,
        @PathVariable entryId: UUID,
        @RequestBody request: CategoryOverrideRequest,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ) {
        accountsService.overrideCategory(accountUid, entryId, request.category, user.userId, user.accountHolderId)
    }

    @DeleteMapping("/{accountUid}/transactions/{entryId}/category")
    @PreAuthorize("hasRole('CUSTOMER')")
    @ResponseStatus(HttpStatus.OK)
    fun clearCategoryOverride(
        @PathVariable accountUid: UUID,
        @PathVariable entryId: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ) {
        accountsService.clearCategoryOverride(accountUid, entryId, user.userId, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/credit-summary", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getCreditSummary(
        @PathVariable accountUid: UUID,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId,
    ): CreditSummaryResponse {
        return accountsService.getCreditSummary(accountUid, user.accountHolderId)
    }

    @GetMapping("/{accountUid}/confirmation-of-funds", produces = [APPLICATION_JSON_VALUE])
    @PreAuthorize("hasAnyRole('CUSTOMER','SUPPORT_AGENT','ADMIN')")
    fun getConfirmationOfFunds(
        @PathVariable accountUid: UUID,
        @RequestParam targetAmountInMinorUnits: Long,
        @AuthenticationPrincipal user: MalaiUserWithAccountHolderId
    ): ConfirmationOfFundsResponse {
        return accountsService.getConfirmationOfFunds(accountUid, user.accountHolderId, targetAmountInMinorUnits)
    }
}
