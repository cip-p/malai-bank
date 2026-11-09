package com.malai.commons.logging

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

/**
 * Recursively redacts JSON values whose keys are in the sensitive-field
 * denylist. Walks objects and arrays at any depth; primitives are returned
 * unchanged. Keys are matched case-insensitively. Pure function — never
 * mutates the input tree.
 *
 * Used by [RequestResponseLoggingFilter] to scrub PII / credential material
 * from request and response bodies before they're written to the audit log.
 *
 * The denylist is intentionally small and explicit. Adding a field name here
 * is a security decision that should be visible in code review, not buried in
 * external config — that's why it's a `Set` constant rather than a Spring
 * property.
 */
internal object BodyRedactor {

    private const val REDACTED = "*****"

    /**
     * Lower-cased field names whose values get replaced with `*****` regardless
     * of where they appear in the JSON tree.
     *
     * - Credentials: `password`, plus the variants used during password change.
     * - Auth tokens: `accessToken`, `refreshToken`, `idToken`, generic `token`,
     *   `secret`, `apiKey`. Bearer values are sensitive even after issuance.
     * - PII: `username` (== email at malai bank), `email`. Treating emails as
     *   PII per GDPR Article 4(1).
     */
    private val SENSITIVE_FIELDS = setOf(
        "password", "currentpassword", "newpassword", "oldpassword",
        "accesstoken", "refreshtoken", "idtoken", "token", "secret", "apikey",
        "username", "email",
    )

    fun redact(node: JsonNode): JsonNode = when (node) {
        is ObjectNode -> redactObject(node)
        is ArrayNode -> redactArray(node)
        else -> node
    }

    private fun redactObject(input: ObjectNode): ObjectNode {
        val out = JsonNodeFactory.instance.objectNode()
        input.properties().forEach { (key, value) ->
            if (key.lowercase() in SENSITIVE_FIELDS) {
                out.put(key, REDACTED)
            } else {
                out.set(key, redact(value))
            }
        }
        return out
    }

    private fun redactArray(input: ArrayNode): ArrayNode {
        val out = JsonNodeFactory.instance.arrayNode()
        input.forEach { out.add(redact(it)) }
        return out
    }
}
