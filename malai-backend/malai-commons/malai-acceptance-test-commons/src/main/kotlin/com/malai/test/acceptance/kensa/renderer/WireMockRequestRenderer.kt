package com.malai.test.acceptance.kensa.renderer

import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.Request
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue

object WireMockRequestRenderer : InteractionRenderer<Request> {

    override fun render(value: Request, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction(
            "Request Body",
            value.bodyAsString?.let(JsonPrettyPrinter::prettyPrint) ?: "N/A",
            Language.Json
        )
    )

    override fun renderAttributes(value: Request): List<RenderedAttributes> = listOf(
        RenderedAttributes(
            "Details",
            setOf(
                NamedValue("Method", value.method),
                NamedValue("Url", value.absoluteUrl)
            ),
        ),
        RenderedAttributes(
            "Headers", value.headers.all().flatMap { it.toNamedValues() }.toSet()
        )
    )

    private fun HttpHeader.toNamedValues(): List<NamedValue> = values.map { NamedValue(key, it) }
}
