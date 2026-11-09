package com.malai.test.acceptance.kensa.renderer

import com.malai.test.acceptance.kensa.interaction.HttpEnvelopeResponse
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue

object HttpEnvelopeResponseRenderer : InteractionRenderer<HttpEnvelopeResponse> {

    override fun render(value: HttpEnvelopeResponse, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction(
            "Response Body",
            value.bodyJson?.let(JsonPrettyPrinter::prettyPrint) ?: "N/A",
            Language.Json,
        )
    )

    override fun renderAttributes(value: HttpEnvelopeResponse): List<RenderedAttributes> = buildList {
        add(RenderedAttributes("Details", setOf(NamedValue("Status", value.status))))
        if (value.headers.isNotEmpty()) {
            add(RenderedAttributes("Headers", value.headers.entries.map { NamedValue(it.key, it.value) }.toSet()))
        }
    }
}
