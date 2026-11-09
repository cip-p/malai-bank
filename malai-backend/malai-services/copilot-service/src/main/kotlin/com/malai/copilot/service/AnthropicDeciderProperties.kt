package com.malai.copilot.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "malai.copilot.anthropic")
data class AnthropicDeciderProperties(
    val apiKey: String? = null,
    val baseUrl: String? = null,
    val model: String = "claude-opus-4-8",
    val effort: String = "low",
    val maxTokens: Long = 4_096L,
    val maxRetries: Int = 0,
    val costCurrency: String = "USD",
    val inputMicrosPerMillionTokens: Long = 5_000_000L,
    val outputMicrosPerMillionTokens: Long = 25_000_000L,
    val cacheWriteMicrosPerMillionTokens: Long = 6_250_000L,
    val cacheReadMicrosPerMillionTokens: Long = 500_000L,
)
