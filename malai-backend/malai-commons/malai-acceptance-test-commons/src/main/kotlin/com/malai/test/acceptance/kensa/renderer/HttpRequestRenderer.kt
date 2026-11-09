package com.malai.test.acceptance.kensa.renderer

import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue
import org.springframework.http.HttpRequest

object HttpRequestRenderer : InteractionRenderer<Pair<HttpRequest, ByteArray>> {

    override fun render(value: Pair<HttpRequest, ByteArray>, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction("Request Body", JsonPrettyPrinter.prettyPrint(String(value.second)), Language.Json)
    )

    override fun renderAttributes(value: Pair<HttpRequest, ByteArray>): List<RenderedAttributes> = listOf(
        RenderedAttributes(
            "Details", setOf(
                NamedValue("Method", value.first.method),
                NamedValue("Url", value.first.uri)
            )
        ),
        RenderedAttributes(
            "Headers", value.first.headers.headerSet().flatMap { it.toNamedValues() }.toSet()
        )
    )

    private fun Map.Entry<String, List<String>>.toNamedValues(): List<NamedValue> = value.map { NamedValue(key, it) }
}
