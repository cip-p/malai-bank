package com.malai.security.chain

import java.net.InetAddress
import java.time.Instant
import java.util.UUID

object CanonicalJson {

    fun serialize(payload: Map<String, Any?>): String = emit(payload)

    private fun emit(value: Any?): String = when (value) {
        null -> "null"
        is Boolean -> if (value) "true" else "false"
        is Number -> value.toString()
        is String -> "\"${escape(value)}\""
        is UUID, is Instant, is InetAddress -> "\"${value}\""
        is Map<*, *> -> emitMap(value)
        is Iterable<*> -> value.joinToString(prefix = "[", postfix = "]", separator = ",", transform = ::emit)
        else -> "\"${escape(value.toString())}\""
    }

    private fun emitMap(map: Map<*, *>): String {
        val entries = map.entries
            .map { (k, v) -> k.toString() to v }
            .sortedBy { it.first }
        return entries.joinToString(prefix = "{", postfix = "}", separator = ",") { (k, v) ->
            "\"${escape(k)}\":${emit(v)}"
        }
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> if (c.code < 0x20) append("\\u%04x".format(c.code)) else append(c)
        }
    }
}
