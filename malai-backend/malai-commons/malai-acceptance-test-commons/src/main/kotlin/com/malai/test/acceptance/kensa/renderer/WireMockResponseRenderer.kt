package com.malai.test.acceptance.kensa.renderer

import com.github.tomakehurst.wiremock.http.HttpHeader
import com.github.tomakehurst.wiremock.http.Response
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue

object WireMockResponseRenderer : InteractionRenderer<Response> {

    override fun render(value: Response, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction(
            "Response Body",
            value.bodyAsString?.let(JsonPrettyPrinter::prettyPrint) ?: "N/A",
            Language.Json
        )
    )

    override fun renderAttributes(value: Response): List<RenderedAttributes> = listOf(
        RenderedAttributes(
            "Status", setOf(NamedValue("Status", value.status)),
        ),
        RenderedAttributes(
            "Headers", value.headers.all().flatMap { it.toNamedValues() }.toSet()
        )
    )

    private fun HttpHeader.toNamedValues(): List<NamedValue> = values.map { NamedValue(key, it) }
}
