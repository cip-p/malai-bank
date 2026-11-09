package com.malai.insights.categoriser

import com.malai.insights.model.Category.BILLS
import com.malai.insights.model.Category.EATING_OUT
import com.malai.insights.model.Category.GENERAL
import com.malai.insights.model.Category.GROCERIES
import com.malai.insights.model.Category.HOUSING
import com.malai.insights.model.Category.INCOME
import com.malai.insights.model.Category.PAYMENTS
import com.malai.insights.model.Category.SUBSCRIPTIONS
import com.malai.insights.model.Category.TRANSFERS
import com.malai.insights.model.Category.TRANSPORT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class CategoriserTest {

    private val categoriser = Categoriser()
    private val anEntryId = UUID.fromString("00000000-0000-4000-7000-000000000001")

    @Test
    fun mapsEachSeededMerchantNarrativeToItsCategory() {
        assertThat(categoriseMerchant("Tesco — weekly shop")).isEqualTo(GROCERIES)
        assertThat(categoriseMerchant("Tesco — top-up")).isEqualTo(GROCERIES)
        assertThat(categoriseMerchant("TfL — daily commute")).isEqualTo(TRANSPORT)
        assertThat(categoriseMerchant("Pret a Manger")).isEqualTo(EATING_OUT)
        assertThat(categoriseMerchant("Octopus Energy — DD")).isEqualTo(BILLS)
        assertThat(categoriseMerchant("Council Tax — DD")).isEqualTo(BILLS)
        assertThat(categoriseMerchant("Rent — SO")).isEqualTo(HOUSING)
        assertThat(categoriseMerchant("Spotify Premium — DD")).isEqualTo(SUBSCRIPTIONS)
        assertThat(categoriseMerchant("Gym membership — SO")).isEqualTo(SUBSCRIPTIONS)
        assertThat(categoriseMerchant("Savings sweep")).isEqualTo(TRANSFERS)
        assertThat(categoriseMerchant("Savings opening balance")).isEqualTo(TRANSFERS)
        assertThat(categoriseMerchant("Credit card payment")).isEqualTo(PAYMENTS)
        assertThat(categoriseMerchant("ACME Salary")).isEqualTo(INCOME)
    }

    @Test
    fun fallsBackToGeneralWhenNoKeywordMatches() {
        assertThat(categoriseMerchant("Corner Shop")).isEqualTo(GENERAL)
        assertThat(categoriseMerchant("J Patel")).isEqualTo(GENERAL)
        assertThat(categoriseMerchant("Cash withdrawal")).isEqualTo(GENERAL)
    }

    @Test
    fun seededGeneralSpendingsFallToGeneral() {
        assertThat(categoriser.categorise(anEntryId, merchantName = "Cash Machine", description = "Cash withdrawal", amountMinorUnits = -6_000L, transactionType = "ATM_WITHDRAWAL"))
            .isEqualTo(GENERAL)
        assertThat(categoriser.categorise(anEntryId, merchantName = "Corner Shop", description = "Corner Shop", amountMinorUnits = -2_500L, transactionType = "CARD_PAYMENT"))
            .isEqualTo(GENERAL)
        assertThat(categoriser.categorise(anEntryId, merchantName = "J Patel", description = "J Patel", amountMinorUnits = -4_000L, transactionType = "CARD_PAYMENT"))
            .isEqualTo(GENERAL)
    }

    @Test
    fun categorisesOnDescriptionWhenMerchantNameIsAbsent() {
        assertThat(categoriser.categorise(anEntryId, merchantName = null, description = "Octopus Energy — DD", amountMinorUnits = -7_800L))
            .isEqualTo(BILLS)
    }

    @Test
    fun merchantNameKeywordWinsOverDescriptionKeyword() {
        assertThat(categoriser.categorise(anEntryId, merchantName = "Tesco — weekly shop", description = "Spotify Premium — DD", amountMinorUnits = -3_000L))
            .isEqualTo(GROCERIES)
    }

    @Test
    fun keywordMatchingIsCaseInsensitive() {
        assertThat(categoriseMerchant("TESCO SUPERSTORE")).isEqualTo(GROCERIES)
        assertThat(categoriseMerchant("acme salary")).isEqualTo(INCOME)
    }

    @Test
    fun overrideAlwaysWinsOverTheRules() {
        assertThat(categoriser.categorise(anEntryId, merchantName = "Tesco — weekly shop", description = null, amountMinorUnits = -3_000L, override = INCOME))
            .isEqualTo(INCOME)
    }

    @Test
    fun categorisesAsGeneralWhenBothMerchantAndDescriptionAreNull() {
        assertThat(categoriser.categorise(anEntryId, merchantName = null, description = null, amountMinorUnits = -1_000L))
            .isEqualTo(GENERAL)
    }

    @Test
    fun fallsBackToIncomeForAnyUnmatchedCredit() {
        assertThat(categoriseByTypeAndSign(transactionType = "PAYMENT_CREDIT", amountMinorUnits = 240_000L)).isEqualTo(INCOME)
        assertThat(categoriseByTypeAndSign(transactionType = "INCOMING_TRANSFER", amountMinorUnits = 50_000L)).isEqualTo(INCOME)
        assertThat(categoriseByTypeAndSign(transactionType = null, amountMinorUnits = 12_345L)).isEqualTo(INCOME)
    }

    @Test
    fun fallsBackToPaymentsForAnUnmatchedRailPaymentDebit() {
        assertThat(categoriseByTypeAndSign(transactionType = "PAYMENT", amountMinorUnits = -4_500L)).isEqualTo(PAYMENTS)
        assertThat(categoriseByTypeAndSign(transactionType = "PAYMENT_SETTLEMENT", amountMinorUnits = -4_500L)).isEqualTo(PAYMENTS)
        assertThat(categoriseByTypeAndSign(transactionType = "DIRECT_DEBIT_COLLECTION", amountMinorUnits = -18_500L)).isEqualTo(PAYMENTS)
    }

    @Test
    fun fallsBackToTransfersForAnUnmatchedBookTransferDebit() {
        assertThat(categoriseByTypeAndSign(transactionType = "TRANSFER", amountMinorUnits = -20_000L)).isEqualTo(TRANSFERS)
    }

    @Test
    fun fallsBackToGeneralForAnUnmatchedDebitWithNoRecognisedType() {
        assertThat(categoriseByTypeAndSign(transactionType = "MYSTERY_RAIL", amountMinorUnits = -1_000L)).isEqualTo(GENERAL)
        assertThat(categoriseByTypeAndSign(transactionType = null, amountMinorUnits = -1_000L)).isEqualTo(GENERAL)
    }

    @Test
    fun keywordRuleWinsOverTheTypeSignDefault() {
        assertThat(categoriser.categorise(anEntryId, merchantName = "Tesco — weekly shop", description = null, amountMinorUnits = -3_000L, transactionType = "PAYMENT"))
            .isEqualTo(GROCERIES)
        assertThat(categoriser.categorise(anEntryId, merchantName = null, description = "ACME Salary", amountMinorUnits = 240_000L, transactionType = "PAYMENT_CREDIT"))
            .isEqualTo(INCOME)
    }

    private fun categoriseByTypeAndSign(transactionType: String?, amountMinorUnits: Long) =
        categoriser.categorise(anEntryId, merchantName = null, description = null, amountMinorUnits = amountMinorUnits, transactionType = transactionType)

    private fun categoriseMerchant(merchantName: String) =
        categoriser.categorise(anEntryId, merchantName = merchantName, description = null, amountMinorUnits = -1_000L)
}
