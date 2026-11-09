package com.malai.aml.sar

import com.malai.aml.monitoring.sha256Hex
import org.springframework.stereotype.Component

@Component
class SarNarrativePrompt {

    private val template: String = javaClass.getResourceAsStream(TEMPLATE_RESOURCE)
        ?.use { it.readBytes().toString(Charsets.UTF_8) }
        ?: error("prompt template $TEMPLATE_RESOURCE is missing from the classpath")

    val templateId: String = "sar-narrative"
    val templateVersion: String = "v1"
    val templateHash: String = sha256Hex(template)

    fun render(timeline: SarTimeline): String = template
        .replace("{{caseId}}", timeline.caseId.toString())
        .replace("{{accountId}}", timeline.accountId.toString())
        .replace("{{openedAt}}", timeline.openedAt.toString())
        .replace("{{timeline}}", timeline.records.joinToString("\n") {
            "  ${it.type.name} ${it.id} at ${it.moment} :: ${it.summary}"
        })

    companion object {
        private const val TEMPLATE_RESOURCE = "/aml/sar-narrative-v1.txt"
    }
}
