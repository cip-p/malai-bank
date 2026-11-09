package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.*

class JointAccountResponseBuilder private constructor() : TestBuilder {

    private lateinit var accountHolderUid: UUID
    private lateinit var personOne: IndividualResponseBuilder
    private lateinit var personTwo: IndividualResponseBuilder

    fun withAccountHolderUid(accountHolderUid: UUID) = apply { this.accountHolderUid = accountHolderUid }
    fun withPersonOne(personOne: IndividualResponseBuilder) = apply { this.personOne = personOne }
    fun withPersonTwo(personTwo: IndividualResponseBuilder) = apply { this.personTwo = personTwo }

    override fun build(): String = JsonSerialiser.toJson(
        JointAccount(accountHolderUid, personOne.buildIndividual(), personTwo.buildIndividual())
    )

    data class JointAccount(
        val accountHolderUid: UUID,
        val personOne: IndividualResponseBuilder.Individual,
        val personTwo: IndividualResponseBuilder.Individual
    )

    companion object {
        fun aJointAccount() = JointAccountResponseBuilder()
    }
}
