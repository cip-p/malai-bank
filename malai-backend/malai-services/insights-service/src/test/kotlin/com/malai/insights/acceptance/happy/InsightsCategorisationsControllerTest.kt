package com.malai.insights.acceptance.happy

import com.malai.insights.acceptance.InsightsAcceptanceTest
import com.malai.insights.model.CategoriseEntriesRequest
import com.malai.insights.model.EntryFact
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class InsightsCategorisationsControllerTest : InsightsAcceptanceTest() {

    private val groceriesEntryId = UUID.fromString("00000000-0000-4000-7000-000000000001")
    private val transportEntryId = UUID.fromString("00000000-0000-4000-7000-000000000002")
    private val eatingOutEntryId = UUID.fromString("00000000-0000-4000-7000-000000000003")
    private val billsEntryId = UUID.fromString("00000000-0000-4000-7000-000000000004")
    private val councilTaxEntryId = UUID.fromString("00000000-0000-4000-7000-000000000005")
    private val housingEntryId = UUID.fromString("00000000-0000-4000-7000-000000000006")
    private val subscriptionsEntryId = UUID.fromString("00000000-0000-4000-7000-000000000007")
    private val gymEntryId = UUID.fromString("00000000-0000-4000-7000-000000000008")
    private val transfersEntryId = UUID.fromString("00000000-0000-4000-7000-000000000009")
    private val paymentsEntryId = UUID.fromString("00000000-0000-4000-7000-00000000000a")
    private val incomeEntryId = UUID.fromString("00000000-0000-4000-7000-00000000000b")
    private val generalEntryId = UUID.fromString("00000000-0000-4000-7000-00000000000c")

    @Test
    fun categorisesEachSeededMerchantNarrativeAndFallsBackToGeneral() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/categorisations")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoriseEntriesRequest(entries = listOf(
                    EntryFact(groceriesEntryId, -3_000L, merchantName = "Tesco — weekly shop"),
                    EntryFact(transportEntryId, -240L, merchantName = "TfL — daily commute"),
                    EntryFact(eatingOutEntryId, -350L, merchantName = "Pret a Manger"),
                    EntryFact(billsEntryId, -7_800L, merchantName = "Octopus Energy — DD"),
                    EntryFact(councilTaxEntryId, -18_500L, merchantName = "Council Tax — DD"),
                    EntryFact(housingEntryId, -150_000L, merchantName = "Rent — SO"),
                    EntryFact(subscriptionsEntryId, -1_099L, merchantName = "Spotify Premium — DD"),
                    EntryFact(gymEntryId, -4_000L, merchantName = "Gym membership — SO"),
                    EntryFact(transfersEntryId, -20_000L, merchantName = "Savings sweep"),
                    EntryFact(paymentsEntryId, -50_000L, merchantName = "Credit card payment"),
                    EntryFact(incomeEntryId, 240_000L, merchantName = "ACME Salary"),
                    EntryFact(generalEntryId, -1_200L, merchantName = "Corner Shop"),
                ))))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categorisations" To listOf(
                    json { "entryId" To groceriesEntryId.toString(); "category" To "GROCERIES"; noExtraProps() },
                    json { "entryId" To transportEntryId.toString(); "category" To "TRANSPORT"; noExtraProps() },
                    json { "entryId" To eatingOutEntryId.toString(); "category" To "EATING_OUT"; noExtraProps() },
                    json { "entryId" To billsEntryId.toString(); "category" To "BILLS"; noExtraProps() },
                    json { "entryId" To councilTaxEntryId.toString(); "category" To "BILLS"; noExtraProps() },
                    json { "entryId" To housingEntryId.toString(); "category" To "HOUSING"; noExtraProps() },
                    json { "entryId" To subscriptionsEntryId.toString(); "category" To "SUBSCRIPTIONS"; noExtraProps() },
                    json { "entryId" To gymEntryId.toString(); "category" To "SUBSCRIPTIONS"; noExtraProps() },
                    json { "entryId" To transfersEntryId.toString(); "category" To "TRANSFERS"; noExtraProps() },
                    json { "entryId" To paymentsEntryId.toString(); "category" To "PAYMENTS"; noExtraProps() },
                    json { "entryId" To incomeEntryId.toString(); "category" To "INCOME"; noExtraProps() },
                    json { "entryId" To generalEntryId.toString(); "category" To "GENERAL"; noExtraProps() },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun categorisesUnmatchedRealPaymentsByTransactionTypeAndSign() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/categorisations")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoriseEntriesRequest(entries = listOf(
                    EntryFact(incomeEntryId, 120_000L, merchantName = null, description = "FPS-REF-8842", transactionType = "PAYMENT_CREDIT"),
                    EntryFact(paymentsEntryId, -4_500L, merchantName = null, description = "FPS-REF-7731", transactionType = "PAYMENT"),
                    EntryFact(transfersEntryId, -20_000L, merchantName = null, description = "internal move", transactionType = "TRANSFER"),
                    EntryFact(generalEntryId, -1_200L, merchantName = null, description = "J Patel", transactionType = "MYSTERY_RAIL"),
                ))))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categorisations" To listOf(
                    json { "entryId" To incomeEntryId.toString(); "category" To "INCOME"; noExtraProps() },
                    json { "entryId" To paymentsEntryId.toString(); "category" To "PAYMENTS"; noExtraProps() },
                    json { "entryId" To transfersEntryId.toString(); "category" To "TRANSFERS"; noExtraProps() },
                    json { "entryId" To generalEntryId.toString(); "category" To "GENERAL"; noExtraProps() },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun categorisesOnDescriptionWhenMerchantNameIsAbsent() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/categorisations")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoriseEntriesRequest(entries = listOf(
                    EntryFact(billsEntryId, -7_800L, merchantName = null, description = "Octopus Energy — DD"),
                ))))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categorisations" To listOf(
                    json { "entryId" To billsEntryId.toString(); "category" To "BILLS"; noExtraProps() },
                )
                noExtraProps()
            }
        }
    }
}
