package com.malai.copilot.service

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "malai.copilot")
data class CopilotProperties(
    val deciderTimeoutMs: Long = 10_000L,
    val armedUngroundedAmountMinorUnits: Long = 161_803L,
    val armedTimeoutAmountMinorUnits: Long = 271_828L,
    val armedErrorAmountMinorUnits: Long = 141_421L,
)
