package com.malai.copilot

import com.anthropic.client.AnthropicClient
import com.malai.copilot.service.AnthropicCopilotDecider
import com.malai.copilot.service.AnthropicDeciderProperties
import com.malai.copilot.service.CopilotDecider
import com.malai.copilot.service.CopilotProperties
import com.malai.copilot.service.TemplateCopilotDecider
import com.malai.copilot.service.anthropicClientWithin
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

private const val DECIDER = "malai.copilot.decider"
private const val TEMPLATE = "template"
private const val ANTHROPIC = "anthropic"

@Configuration
class CopilotDeciderConfiguration {

    @Bean
    @ConditionalOnProperty(name = [DECIDER], havingValue = TEMPLATE, matchIfMissing = true)
    fun templateCopilotDecider(properties: CopilotProperties): CopilotDecider = TemplateCopilotDecider(properties)

    @Configuration
    @ConditionalOnProperty(name = [DECIDER], havingValue = ANTHROPIC)
    @EnableConfigurationProperties(AnthropicDeciderProperties::class)
    class AnthropicDeciderConfiguration {

        @Bean
        fun anthropicClient(
            properties: AnthropicDeciderProperties,
            copilotProperties: CopilotProperties,
        ): AnthropicClient = anthropicClientWithin(properties, Duration.ofMillis(copilotProperties.deciderTimeoutMs))

        @Bean
        fun anthropicCopilotDecider(
            client: AnthropicClient,
            properties: AnthropicDeciderProperties,
        ): CopilotDecider = AnthropicCopilotDecider(client, properties)
    }
}
