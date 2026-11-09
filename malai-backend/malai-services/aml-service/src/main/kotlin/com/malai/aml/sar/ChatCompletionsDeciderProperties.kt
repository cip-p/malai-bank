package com.malai.aml.sar

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "malai.aml.sar.chat-completions")
data class ChatCompletionsDeciderProperties(
    val baseUrl: String? = null,
    val apiKey: String? = null,
    val model: String = "qwen2.5:7b-instruct",
    val maxTokens: Long = 4_096L,
    val costCurrency: String = "USD",
    val inputMicrosPerMillionTokens: Long = 0L,
    val outputMicrosPerMillionTokens: Long = 0L,
)
