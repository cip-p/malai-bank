package com.malai.aml.sar

import com.malai.aml.monitoring.AmlProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

private const val DECIDER = "malai.aml.sar.decider"
private const val TEMPLATE = "template"
private const val CHAT_COMPLETIONS = "chat-completions"

@Configuration
class SarDeciderConfiguration {

    @Bean
    @ConditionalOnProperty(name = [DECIDER], havingValue = TEMPLATE, matchIfMissing = true)
    fun templateSarDraftDecider(properties: AmlProperties): SarDraftDecider = TemplateSarDraftDecider(properties)

    @Configuration
    @ConditionalOnProperty(name = [DECIDER], havingValue = CHAT_COMPLETIONS)
    @EnableConfigurationProperties(ChatCompletionsDeciderProperties::class)
    class ChatCompletionsDeciderConfiguration {

        @Bean
        fun chatCompletionsSarDraftDecider(
            properties: ChatCompletionsDeciderProperties,
            amlProperties: AmlProperties,
        ): SarDraftDecider = ChatCompletionsSarDraftDecider(
            properties,
            Duration.ofMillis(amlProperties.sar.deciderTimeoutMs),
        )
    }
}
