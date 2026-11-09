package com.malai.client.malai.accountholder.builders.response

import com.malai.client.malai.JsonSerialiser
import com.malai.client.malai.TestBuilder

class SoleTraderResponseBuilder private constructor() : TestBuilder {

    private lateinit var tradingAsName: String
    private var businessCategory: String? = null
    private var businessSubCategory: String? = null

    fun withTradingAsName(tradingAsName: String) = apply { this.tradingAsName = tradingAsName }
    fun withBusinessCategory(businessCategory: String?) = apply { this.businessCategory = businessCategory }
    fun withBusinessSubCategory(businessSubCategory: String?) = apply { this.businessSubCategory = businessSubCategory }

    override fun build(): String = JsonSerialiser.toJson(
        SoleTrader(tradingAsName, businessCategory, businessSubCategory)
    )

    data class SoleTrader(
        val tradingAsName: String,
        val businessCategory: String?,
        val businessSubCategory: String?
    )

    companion object {
        fun aSoleTrader() = SoleTraderResponseBuilder()
    }
}
