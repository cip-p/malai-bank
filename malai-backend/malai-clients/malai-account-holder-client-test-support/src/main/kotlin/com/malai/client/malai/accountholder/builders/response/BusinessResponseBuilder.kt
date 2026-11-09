package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class BusinessResponseBuilder private constructor() : TestBuilder {

    private lateinit var companyName: String
    private var companyType: String? = null
    private var companyCategory: String? = null
    private var companySubCategory: String? = null
    private var companyRegistrationNumber: String? = null
    private lateinit var email: String
    private lateinit var phone: String

    fun withCompanyName(companyName: String) = apply { this.companyName = companyName }
    fun withCompanyType(companyType: String?) = apply { this.companyType = companyType }
    fun withCompanyCategory(companyCategory: String?) = apply { this.companyCategory = companyCategory }
    fun withCompanySubCategory(companySubCategory: String?) = apply { this.companySubCategory = companySubCategory }
    fun withCompanyRegistrationNumber(companyRegistrationNumber: String?) = apply { this.companyRegistrationNumber = companyRegistrationNumber }
    fun withEmail(email: String) = apply { this.email = email }
    fun withPhone(phone: String) = apply { this.phone = phone }

    override fun build(): String = JsonSerialiser.toJson(
        Business(companyName, companyType, companyCategory, companySubCategory, companyRegistrationNumber, email, phone)
    )

    data class Business(
        val companyName: String,
        val companyType: String?,
        val companyCategory: String?,
        val companySubCategory: String?,
        val companyRegistrationNumber: String?,
        val email: String,
        val phone: String
    )

    companion object {
        fun aBusiness() = BusinessResponseBuilder()
    }
}
