package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class AccountAuthorisationsResponseBuilder private constructor() : TestBuilder {

    private val authorisations: MutableList<AccountAuthorisationResponseBuilder> = mutableListOf()

    fun withAuthorisations(vararg authorisations: AccountAuthorisationResponseBuilder) =
        apply { this.authorisations.addAll(authorisations) }

    override fun build(): String = JsonSerialiser.toJson(AccountAuthorisations(authorisations.map { it.toModel() }))

    data class AccountAuthorisations(val authorisations: List<AccountAuthorisationResponseBuilder.AccountAuthorisation>)

    companion object {
        fun anAccountAuthorisations() = AccountAuthorisationsResponseBuilder()
    }
}
