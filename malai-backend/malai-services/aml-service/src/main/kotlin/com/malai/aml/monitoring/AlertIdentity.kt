package com.malai.aml.monitoring

import java.util.UUID

object AlertIdentity {

    fun evidenceHash(accountId: UUID, typology: AmlTypology, evidencePaymentIds: List<UUID>): String =
        sha256Hex("$accountId\n${typology.name}\n${sortedIdLines(evidencePaymentIds)}")

    fun alertDecisionId(evidenceHash: String): UUID = nameUuid("aml-alert-decision:$evidenceHash")

    fun alertEntryId(evidenceHash: String): UUID = nameUuid("aml-alert-entry:$evidenceHash")

    fun evaluationFailureHash(accountId: UUID, windowPaymentIds: List<UUID>): String =
        sha256Hex("$accountId\nEVALUATION_FAILURE\n${sortedIdLines(windowPaymentIds)}")

    fun evaluationFailureDecisionId(failureHash: String): UUID = nameUuid("aml-evaluation-failure-decision:$failureHash")

    fun evaluationFailureEntryId(failureHash: String): UUID = nameUuid("aml-evaluation-failure-entry:$failureHash")

    private fun sortedIdLines(paymentIds: List<UUID>): String =
        paymentIds.map(UUID::toString).sorted().joinToString("\n")

    private fun nameUuid(name: String): UUID = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8))
}
