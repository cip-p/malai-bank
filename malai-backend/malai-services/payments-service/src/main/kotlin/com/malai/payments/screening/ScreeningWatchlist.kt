package com.malai.payments.screening

import org.springframework.stereotype.Component

@Component
class ScreeningWatchlist(properties: ScreeningProperties) {

    val version: String = properties.watchlist.version

    private val names: List<String> = properties.watchlist.names.map { it.trim() }.sorted()

    val entries: String = names.joinToString(", ")

    val contentHash: String = sha256Hex((listOf(version) + names).joinToString("\n"))

    fun flags(beneficiaryName: String?): Boolean =
        beneficiaryName != null && names.any { it.equals(beneficiaryName.trim(), ignoreCase = true) }
}
