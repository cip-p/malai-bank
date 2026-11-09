package com.malai.scheme.contracts.card

object CardDeclineReasonCodes {

    private val schemeCodeToCanonicalName: Map<String, String> = mapOf(
        "62" to "CARD_FROZEN",
        "04" to "CARD_CANCELLED",
        "51" to "INSUFFICIENT_FUNDS",
        "61" to "OVER_LIMIT",
        "14" to "UNKNOWN_CARD",
    )

    private val canonicalNameToSchemeCode: Map<String, String> =
        schemeCodeToCanonicalName.entries.associate { (code, name) -> name to code }

    fun canonicalReasonName(schemeCode: String): String =
        schemeCodeToCanonicalName[schemeCode]
            ?: error("unknown card scheme decline reason code: $schemeCode")

    fun schemeCode(canonicalReasonName: String): String =
        canonicalNameToSchemeCode[canonicalReasonName]
            ?: error("no card scheme decline reason code for canonical reason: $canonicalReasonName")
}
