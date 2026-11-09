package com.malai.payments.fx

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@ConfigurationProperties(prefix = "malai.fx")
data class StubFxRatesProperties(
    val rates: Map<String, String> = emptyMap(),
)

@Configuration
@EnableConfigurationProperties(StubFxRatesProperties::class)
class FxConversionConfiguration
