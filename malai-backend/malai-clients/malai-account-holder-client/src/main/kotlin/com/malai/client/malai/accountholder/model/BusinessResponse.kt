package com.malai.client.malai.accountholder.model

data class BusinessResponse(
    val companyName: String,
    val companyType: String?,
    val companyCategory: String?,
    val companySubCategory: String?,
    val companyRegistrationNumber: String?,
    val email: String,
    val phone: String
)
