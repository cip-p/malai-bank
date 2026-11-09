package com.malai.client.malai.card.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class AuthoriseCardResponseBuilder private constructor() : TestBuilder {

    private lateinit var authorisationId: UUID
    private lateinit var decision: String
    private var declineReason: String? = null

    fun withAuthorisationId(authorisationId: UUID) = apply { this.authorisationId = authorisationId }
    fun withDecision(decision: String) = apply { this.decision = decision }
    fun withDeclineReason(declineReason: String?) = apply { this.declineReason = declineReason }

    override fun build(): String =
        JsonSerialiser.toJson(AuthoriseCard(authorisationId, decision, declineReason))

    data class AuthoriseCard(
        val authorisationId: UUID,
        val decision: String,
        val declineReason: String?,
    )

    companion object {
        fun anApprovedAuthorisation() = AuthoriseCardResponseBuilder()
            .withAuthorisationId(UUID.randomUUID())
            .withDecision("APPROVED")

        fun aDeclinedAuthorisation() = AuthoriseCardResponseBuilder()
            .withAuthorisationId(UUID.randomUUID())
            .withDecision("DECLINED")
            .withDeclineReason("INSUFFICIENT_FUNDS")
    }
}
