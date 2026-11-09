package com.malai.aml.monitoring

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

@ConfigurationProperties(prefix = "malai.aml")
data class AmlProperties(
    val structuring: Structuring = Structuring(),
    val rapidInOut: RapidInOut = RapidInOut(),
    val jurisdictionList: JurisdictionList = JurisdictionList(),
    val sar: Sar = Sar(),
    val armedTimeoutAmountMinorUnits: Long = 173_205L,
    val armedErrorAmountMinorUnits: Long = 223_606L,
) {
    data class Structuring(
        val window: Duration = Duration.ofHours(1),
        val minPayments: Int = 3,
        val thresholdMinorUnits: Long = 1_000_000L,
    )

    data class RapidInOut(
        val window: Duration = Duration.ofMinutes(15),
        val minOutboundPercent: Int = 80,
    )

    data class JurisdictionList(
        val version: String = "2026-07-01",
        val codes: List<String> = listOf("QM", "ZZ"),
    )

    data class Sar(
        val decider: String = "template",
        val deciderTimeoutMs: Long = 10_000L,
        val armedUngroundedAmountMinorUnits: Long = 314_159L,
        val armedTimeoutAmountMinorUnits: Long = 577_215L,
        val armedErrorAmountMinorUnits: Long = 693_147L,
    )
}

@Configuration
@EnableConfigurationProperties(AmlProperties::class)
class AmlMonitoringConfiguration
