package com.malai.test.acceptance.kensa.renderer

import tools.jackson.databind.ObjectMapper

/**
 * Pretty-prints a JSON body for display in a Kensa sequence diagram. If the input is not
 * valid JSON (e.g. empty body, form-encoded, plain text) the original string is returned
 * unchanged so non-JSON interactions still render meaningfully.
 */
object JsonPrettyPrinter {

    private val mapper = ObjectMapper()
    private val prettyWriter = mapper.writerWithDefaultPrettyPrinter()

    fun prettyPrint(body: String?): String {
        if (body.isNullOrBlank()) return body ?: ""
        return runCatching { prettyWriter.writeValueAsString(mapper.readTree(body)) }
            .getOrDefault(body)
    }
}
