package com.malai.security.audit

import org.slf4j.LoggerFactory

// Defence-in-depth redaction for AuditEvent.beforeState / afterState (review #2).
// The state maps are typed Map<String, Any?> -- nothing structurally prevents a
// future caller from accidentally putting a password, PAN, CVV, or token into
// the audit row. This redactor scans the state map (recursively into nested
// maps + lists) and replaces values whose key tokens match a known-sensitive
// name with the literal string "[REDACTED]".
//
// The redacted map is what gets:
//   * canonicalised + hashed into the chain (so the chain hashes the redacted form)
//   * persisted into audit_events.before_state / after_state JSONB columns
//
// Both ends agree -- writer and verifier both go through this function.
//
// Posture: silent redaction + WARN log when a hit fires. The warning is the bug
// signal -- a developer who tried to log a password sees it in the logs and has
// to fix the call site. We deliberately do NOT throw, so a regulator-facing
// audit row is never lost over a developer mistake. A stricter "fail closed"
// mode is a follow-up if SOC wants it.
//
// Matching is token-based, not substring-based, so 'panel' is NOT redacted but
// 'pan_number' / 'panNumber' / 'PAN' all are. The key is split on non-
// alphanumeric chars AND on camelCase boundaries; each lowercased token is
// checked against SENSITIVE_TOKENS, and the concatenated joined form is checked
// against SENSITIVE_PHRASES (for multi-word names like 'cardNumber').
object AuditRedactor {

    private val log = LoggerFactory.getLogger(javaClass)

    private const val REDACTED = "[REDACTED]"

    // Single-word sensitive tokens. A key matches if any of its lowercased
    // tokens equals one of these (exact match, no prefix/suffix games).
    private val SENSITIVE_TOKENS = setOf(
        "password", "passwd", "passcode",
        "pan",
        "cvv", "cvc",
        "pin",
        "secret", "secrets",
        "ssn", "nin",
    )

    // Multi-word sensitive phrases. A key matches if its joined-token form (no
    // separators, lowercased) contains one of these. Use this when the
    // sensitive name is naturally split across words ('access token',
    // 'card number') and the individual tokens ('access', 'token', 'card',
    // 'number') are too generic to redact on their own.
    private val SENSITIVE_PHRASES = setOf(
        "cardnumber",
        "primaryaccountnumber",
        "accesstoken",
        "refreshtoken",
        "idtoken",
        "bearertoken",
        "sessiontoken",
        "privatekey",
        "apikey",
        "nationalinsurancenumber",
    )

    private val TOKEN_SPLIT = Regex("[^A-Za-z0-9]+|(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])")

    // Value-shape redaction for card PANs: the key-based match above only fires on a
    // sensibly-named key. A card **Card**'s PAN must never reach the chain even if a
    // caller puts it under an innocuous key or embeds it in free text, so we also strip
    // any bare 16-digit number in the malai 9-series (MII 9, the unassigned range the
    // malai BIN lives in) from every String value, wherever it appears. Anchored on
    // non-alphanumeric boundaries so neither a longer digit run nor a digit run inside a
    // hex digest (a content hash, a chain hash) is partially masked.
    private val PAN_VALUE = Regex("(?<![0-9A-Za-z])9\\d{15}(?![0-9A-Za-z])")

    fun redact(state: Map<String, Any?>?): Map<String, Any?>? = state?.let { redactMap(it) }

    private fun redactMap(map: Map<*, *>): Map<String, Any?> = map.entries.associate { (k, v) ->
        val key = k.toString()
        if (isSensitive(key)) {
            log.warn("audit redaction: stripped value for sensitive key '{}'", key)
            key to REDACTED
        } else {
            key to redactValue(v)
        }
    }

    private fun redactValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> redactMap(value)
        is Iterable<*> -> value.map(::redactValue)
        is String -> redactPan(value)
        else -> value
    }

    private fun redactPan(value: String): String {
        val redacted = PAN_VALUE.replace(value, REDACTED)
        if (redacted != value) {
            log.warn("audit redaction: stripped a card number from a value")
        }
        return redacted
    }

    private fun isSensitive(key: String): Boolean {
        val tokens = key.split(TOKEN_SPLIT).filter(String::isNotEmpty).map(String::lowercase)
        if (tokens.any { it in SENSITIVE_TOKENS }) return true
        val joined = tokens.joinToString("")
        return SENSITIVE_PHRASES.any { joined.contains(it) }
    }
}
