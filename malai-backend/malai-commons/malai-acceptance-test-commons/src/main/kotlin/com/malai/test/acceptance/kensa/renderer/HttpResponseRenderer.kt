package com.malai.test.acceptance.kensa.renderer

import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue
import org.springframework.http.client.ClientHttpResponse

object HttpResponseRenderer : InteractionRenderer<ClientHttpResponse> {

    override fun render(value: ClientHttpResponse, attributes: Attributes): List<RenderedInteraction> = listOfNotNull(
        RenderedInteraction("Response Body", JsonPrettyPrinter.prettyPrint(String(value.body.readBytes())), Language.Json)
    )

    override fun renderAttributes(value: ClientHttpResponse): List<RenderedAttributes> = listOf(
        RenderedAttributes(
            "Details", setOf(NamedValue("Status", value.statusCode)),
        ),
        RenderedAttributes(
            "Headers", value.headers.headerSet().flatMap { it.toNamedValues() }.toSet()
        )
    )

    private fun Map.Entry<String, List<String>>.toNamedValues(): List<NamedValue> = value.map { NamedValue(key, it) }

}
