package com.malai.stub.directory

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

@Configuration
@EnableConfigurationProperties(StubMerchantDirectoryProperties::class)
class StubMerchantDirectoryConfiguration

@Component
class StubMerchantDirectory(private val properties: StubMerchantDirectoryProperties) {

    private val byId: Map<String, StubMerchant> = properties.merchants.associateBy { it.merchantId }

    fun find(merchantId: String): StubMerchant? = byId[merchantId]

    fun all(): List<StubMerchant> = properties.merchants
}

@ConfigurationProperties(prefix = "malai.stub.card")
data class StubMerchantDirectoryProperties(val merchants: List<StubMerchant> = emptyList())

data class StubMerchant(
    val merchantId: String,
    val name: String,
    val mcc: String,
)
