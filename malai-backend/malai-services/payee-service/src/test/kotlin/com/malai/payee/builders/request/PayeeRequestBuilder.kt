package com.malai.payee.builders.request

import com.malai.client.malai.JsonSerialiser.Companion.toJson
import com.malai.client.malai.TestBuilder

/**
 * Test-scope builder for `POST /api/v1/payees` (Plan 02-14 saved-payee CRUD).
 *
 * Builders live in `payee-service` test sources rather than a `malai-payee-client-test-support`
 * module because we don't have cross-service consumers yet (project convention:
 * "no unused -client modules"). Promote when an e2e or other service needs them.
 */
class PayeeRequestBuilder private constructor() : TestBuilder {

    private var name: String? = null
    private var sortCode: String? = null
    private var accountNumber: String? = null
    private var nickname: String? = null

    fun withName(name: String?) = apply { this.name = name }
    fun withSortCode(sortCode: String?) = apply { this.sortCode = sortCode }
    fun withAccountNumber(accountNumber: String?) = apply { this.accountNumber = accountNumber }
    fun withNickname(nickname: String?) = apply { this.nickname = nickname }

    override fun build(): String = toJson(PayeeRequestPayload(name, sortCode, accountNumber, nickname))

    private data class PayeeRequestPayload(
        val name: String?,
        val sortCode: String?,
        val accountNumber: String?,
        val nickname: String?,
    )

    companion object {
        fun aPayeeRequest() = PayeeRequestBuilder()
            .withName("Mum")
            .withSortCode("12-34-56")
            .withAccountNumber("11111111")
    }
}
