package com.malai.stub.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

class StubCopLookupRequestBuilder private constructor() : TestBuilder {

    private var sortCode: String? = null
    private var accountNumber: String? = null
    private var name: String? = null

    fun withSortCode(sortCode: String?) = apply { this.sortCode = sortCode }
    fun withAccountNumber(accountNumber: String?) = apply { this.accountNumber = accountNumber }
    fun withName(name: String?) = apply { this.name = name }

    override fun build(): String = toJson(StubCopLookupRequestPayload(sortCode, accountNumber, name))

    private data class StubCopLookupRequestPayload(
        val sortCode: String?,
        val accountNumber: String?,
        val name: String?,
    )

    companion object {
        fun aStubCopLookupRequest() = StubCopLookupRequestBuilder()
            .withSortCode("60-00-01")
            .withAccountNumber("12345678")
            .withName("Alice Walker")
    }
}
