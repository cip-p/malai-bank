package com.malai.aml.monitoring

import org.springframework.stereotype.Component

@Component
class HighRiskJurisdictionList(properties: AmlProperties) {

    val version: String = properties.jurisdictionList.version

    private val codes: List<String> = properties.jurisdictionList.codes.map { it.trim().uppercase() }.sorted()

    val entries: String = codes.joinToString(", ")

    val contentHash: String = sha256Hex((listOf(version) + codes).joinToString("\n"))

    fun flags(jurisdiction: String?): Boolean = jurisdiction != null && codes.contains(jurisdiction.uppercase())
}
