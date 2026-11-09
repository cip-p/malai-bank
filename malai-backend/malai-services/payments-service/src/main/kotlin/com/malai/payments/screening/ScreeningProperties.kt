package com.malai.payments.screening

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

enum class ScreeningMode { SHADOW, ENFORCE }

@ConfigurationProperties(prefix = "malai.payments.screening")
data class ScreeningProperties(
    var mode: ScreeningMode = ScreeningMode.SHADOW,
    val thresholdMinorUnits: Long = 1_000_000L,
    val armedTimeoutAmountMinorUnits: Long = 314_159L,
    val watchlist: Watchlist = Watchlist(),
) {
    data class Watchlist(
        val version: String = "2026-07-01",
        val names: List<String> = listOf("Carol Singh"),
    )
}

@Configuration
@EnableConfigurationProperties(ScreeningProperties::class)
class ScreeningConfiguration
