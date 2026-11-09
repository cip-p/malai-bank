package com.malai.client.malai.accountholder.model

import java.util.*

data class JointAccountResponse(
    val accountHolderUid: UUID,
    val personOne: IndividualResponse,
    val personTwo: IndividualResponse
)
