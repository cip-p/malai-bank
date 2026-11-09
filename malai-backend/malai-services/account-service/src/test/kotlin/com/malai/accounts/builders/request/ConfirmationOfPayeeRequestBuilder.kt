package com.malai.accounts.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class ConfirmationOfPayeeRequestBuilder private constructor() : TestBuilder {

    private var name: String? = null
    private var sortCode: String? = null
    private var accountNumber: String? = null

    fun withName(name: String?) = apply { this.name = name }
    fun withSortCode(sortCode: String?) = apply { this.sortCode = sortCode }
    fun withAccountNumber(accountNumber: String?) = apply { this.accountNumber = accountNumber }

    override fun build(): String = toJson(ConfirmationOfPayeeRequestPayload(name, sortCode, accountNumber))

    private data class ConfirmationOfPayeeRequestPayload(
        val name: String?,
        val sortCode: String?,
        val accountNumber: String?,
    )

    companion object {
        fun aConfirmationOfPayeeRequest() = ConfirmationOfPayeeRequestBuilder()
            .withName("Tesco")
            .withSortCode("65-82-37")
            .withAccountNumber("33445566")
    }
}
