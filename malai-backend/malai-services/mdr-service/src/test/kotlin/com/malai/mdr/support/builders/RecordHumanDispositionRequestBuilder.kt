package com.malai.mdr.support.builders

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder
import java.util.UUID

class RecordHumanDispositionRequestBuilder private constructor() : TestBuilder {

    private var entryId: String? = null
    private var disposition: String? = null
    private var editedOutput: String? = null

    fun withEntryId(entryId: UUID?) = apply { this.entryId = entryId?.toString() }
    fun withDisposition(disposition: String?) = apply { this.disposition = disposition }
    fun withEditedOutput(editedOutput: String?) = apply { this.editedOutput = editedOutput }

    override fun build(): String = JsonSerialiser.toJson(
        RecordHumanDispositionRequestJson(entryId, disposition, editedOutput),
    )

    data class RecordHumanDispositionRequestJson(
        val entryId: String?,
        val disposition: String?,
        val editedOutput: String?,
    )

    companion object {
        fun anAcceptDispositionRequest() = RecordHumanDispositionRequestBuilder()
            .withEntryId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
            .withDisposition("ACCEPT")

        fun aRejectDispositionRequest() = RecordHumanDispositionRequestBuilder()
            .withEntryId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
            .withDisposition("REJECT")

        fun anEditDispositionRequest() = RecordHumanDispositionRequestBuilder()
            .withEntryId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
            .withDisposition("EDIT")
            .withEditedOutput("APPROVE with monitoring: raise the review threshold.")
    }
}
