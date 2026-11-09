package com.malai.scheme.contracts.bacs

object BacsReturnReasonCodes {

    private val schemeCodeToCanonicalName: Map<String, String> = mapOf(
        "0" to "INSUFFICIENT_FUNDS",
        "5" to "INVALID_ACCOUNT",
    )

    private val canonicalNameToSchemeCode: Map<String, String> =
        schemeCodeToCanonicalName.entries.associate { (code, name) -> name to code }

    fun canonicalReasonName(schemeCode: String): String =
        schemeCodeToCanonicalName[schemeCode]
            ?: error("unknown BACS scheme return reason code: $schemeCode")

    fun schemeCode(canonicalReasonName: String): String =
        canonicalNameToSchemeCode[canonicalReasonName]
            ?: error("no BACS scheme return reason code for canonical reason: $canonicalReasonName")
}
