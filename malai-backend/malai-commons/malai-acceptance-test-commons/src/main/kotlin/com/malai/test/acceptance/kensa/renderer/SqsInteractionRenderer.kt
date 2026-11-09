package com.malai.test.acceptance.kensa.renderer

import com.malai.test.acceptance.kensa.interaction.SqsInteraction
import dev.kensa.render.InteractionRenderer
import dev.kensa.render.Language
import dev.kensa.render.RenderedAttributes
import dev.kensa.render.RenderedInteraction
import dev.kensa.util.Attributes
import dev.kensa.util.NamedValue

object SqsInteractionRenderer : InteractionRenderer<SqsInteraction> {

    override fun render(value: SqsInteraction, attributes: Attributes): List<RenderedInteraction> = listOf(
        RenderedInteraction("Message Body", JsonPrettyPrinter.prettyPrint(value.bodyJson), Language.Json)
    )

    override fun renderAttributes(value: SqsInteraction): List<RenderedAttributes> = buildList {
        add(RenderedAttributes("Details", setOf(NamedValue("Queue", value.queueBaseName))))
        if (value.headers.isNotEmpty()) {
            add(RenderedAttributes("Headers", value.headers.entries.map { NamedValue(it.key, it.value) }.toSet()))
        }
    }
}
