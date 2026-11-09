package com.malai.copilot.model

import java.security.MessageDigest

fun sha256Hex(content: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(content.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

fun maskIdentifier(identifier: String): String =
    "*".repeat(maxOf(0, identifier.length - 4)) + identifier.takeLast(4)

private val MASKED_AUDIT_STATE_KEYS = listOf("accountnumber", "accountno", "iban")

fun maskedAuditState(afterState: Map<String, Any?>): Map<String, Any?> =
    afterState.mapValues { (key, value) ->
        if (namesAnAccountIdentifier(key) && value is String && value.isNotEmpty()) {
            maskIdentifier(value)
        } else {
            value
        }
    }

private fun namesAnAccountIdentifier(key: String): Boolean =
    key.lowercase().let { lowered -> MASKED_AUDIT_STATE_KEYS.any { it in lowered } }
