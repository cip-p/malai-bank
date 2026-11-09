package com.malai.scheme.contracts.fps

object FpsReturnReasonCodes {

    private val schemeCodeToCanonicalName: Map<String, String> = mapOf(
        "AC04" to "ACCOUNT_CLOSED",
        "AC03" to "ACCOUNT_NAME_MISMATCH",
        "AB07" to "RECIPIENT_BANK_UNAVAILABLE",
        "MD07" to "BENEFICIARY_DECEASED",
        "AC01" to "INVALID_ACCOUNT",
    )

    private val canonicalNameToSchemeCode: Map<String, String> =
        schemeCodeToCanonicalName.entries.associate { (code, name) -> name to code }

    fun canonicalReasonName(schemeCode: String): String =
        schemeCodeToCanonicalName[schemeCode]
            ?: error("unknown FPS scheme return reason code: $schemeCode")

    fun schemeCode(canonicalReasonName: String): String =
        canonicalNameToSchemeCode[canonicalReasonName]
            ?: error("no FPS scheme return reason code for canonical reason: $canonicalReasonName")
}
