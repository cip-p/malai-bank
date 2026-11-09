package com.malai.accountholder.model

import java.util.*

data class AccountHolderResponse(
    val accountHolderUid: UUID,
    val accountHolderType: String
)

data class AccountHolderNameResponse(
    val accountHolderName: String
)

data class AccountHolderSummaryResponse(
    val accountHolderUid: UUID,
    val accountHolderName: String,
    val accountHolderType: String
)

data class AccountHoldersResponse(
    val accountHolders: List<AccountHolderSummaryResponse>
)

data class IndividualResponse(
    val title: String?,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val email: String,
    val phone: String
)

data class JointAccountResponse(
    val accountHolderUid: UUID,
    val personOne: IndividualResponse,
    val personTwo: IndividualResponse
)

data class BusinessResponse(
    val companyName: String,
    val companyType: String?,
    val companyCategory: String?,
    val companySubCategory: String?,
    val companyRegistrationNumber: String?,
    val email: String,
    val phone: String
)

data class AddressResponse(
    val line1: String,
    val line2: String?,
    val line3: String?,
    val postTown: String,
    val postCode: String,
    val countryCode: String
)

data class SoleTraderResponse(
    val tradingAsName: String,
    val businessCategory: String?,
    val businessSubCategory: String?
)

data class AddressesResponse(
    val current: AddressResponse?,
    val previous: List<AddressResponse>
)

data class CreateAccountHolderRequest(
    val accountHolderType: String
)

data class CreateIndividualRequest(
    val title: String?,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String,
    val email: String,
    val phone: String
)

data class UpdateEmailRequest(
    val email: String
)

data class AddressUpdateRequest(
    val line1: String,
    val line2: String?,
    val line3: String?,
    val postTown: String,
    val postCode: String,
    val countryCode: String,
    val udprn: String?,
    val umprn: String?,
    val from: String
)

data class SettleUpProfileResponse(
    val settleUpLink: String,
    val status: String
)
