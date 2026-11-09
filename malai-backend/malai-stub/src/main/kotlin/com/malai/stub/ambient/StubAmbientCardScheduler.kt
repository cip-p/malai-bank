package com.malai.stub.ambient

import com.malai.stub.directory.StubMerchant
import com.malai.stub.directory.StubMerchantDirectory
import com.malai.stub.directory.StubPanRegistry
import com.malai.stub.scheme.StubCardScheme
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Configuration
@EnableConfigurationProperties(StubAmbientCardProperties::class)
class StubAmbientCardConfiguration

@Component
@ConditionalOnProperty(
    prefix = "malai.stub.ambient.card",
    name = ["enabled"],
    havingValue = "true",
)
class StubAmbientCardScheduler(
    private val properties: StubAmbientCardProperties,
    private val panRegistry: StubPanRegistry,
    private val merchants: StubMerchantDirectory,
    private val stubCardScheme: StubCardScheme,
) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val purchaseCount = AtomicInteger(0)

    @Scheduled(cron = "\${malai.stub.ambient.card.cron:0 0 * * * *}")
    fun tick() {
        val registeredPans = panRegistry.all()
        if (registeredPans.isEmpty()) {
            log.debug("ambient card spend: no registered PANs, nothing to originate")
            return
        }
        val directory = merchants.all()
        val amounts = properties.amountsMinorUnits
        if (directory.isEmpty() || amounts.isEmpty()) {
            log.warn("ambient card spend: no merchants or amounts configured, skipping")
            return
        }
        for (pan in registeredPans) {
            originate(pan, directory, amounts)
        }
    }

    private fun originate(pan: String, directory: List<StubMerchant>, amounts: List<Long>) {
        val index = purchaseCount.getAndIncrement()
        val merchant = directory[Math.floorMod(index, directory.size)]
        val amount = amounts[Math.floorMod(index, amounts.size)]
        stubCardScheme.originateAuthorisation(
            pan = pan,
            merchantId = merchant.merchantId,
            amountMinorUnits = amount,
            currency = properties.currency,
        )
        log.info(
            "ambient card spend: originated pan={} merchant={} amount={}",
            maskPan(pan), merchant.name, amount,
        )
    }

    private fun maskPan(pan: String): String =
        if (pan.length >= 4) "*".repeat(pan.length - 4) + pan.takeLast(4) else "****"
}

@ConfigurationProperties(prefix = "malai.stub.ambient.card")
data class StubAmbientCardProperties(
    val enabled: Boolean = false,
    val cron: String = "0 0 * * * *",
    val amountsMinorUnits: List<Long> = listOf(320L, 875L, 1450L, 640L, 2199L),
    val currency: String = "GBP",
)
