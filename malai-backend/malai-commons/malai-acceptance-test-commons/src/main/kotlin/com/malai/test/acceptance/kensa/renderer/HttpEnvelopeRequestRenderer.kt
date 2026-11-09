package com.malai.test.acceptance.kensa.renderer

import com.malai.test.acceptance.kensa.interaction.HttpEnvelopeRequest
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue

object HttpEnvelopeRequestRenderer : InteractionRenderer<HttpEnvelopeRequest> {

    override fun render(value: HttpEnvelopeRequest, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction(
            "Request Body",
            value.bodyJson?.let(JsonPrettyPrinter::prettyPrint) ?: "N/A",
            Language.Json,
        )
    )

    override fun renderAttributes(value: HttpEnvelopeRequest): List<RenderedAttributes> = buildList {
        add(RenderedAttributes("Details", setOf(
            NamedValue("Method", value.method),
            NamedValue("Url", value.url),
        )))
        if (value.headers.isNotEmpty()) {
            add(RenderedAttributes("Headers", value.headers.entries.map { NamedValue(it.key, it.value) }.toSet()))
        }
    }
}
