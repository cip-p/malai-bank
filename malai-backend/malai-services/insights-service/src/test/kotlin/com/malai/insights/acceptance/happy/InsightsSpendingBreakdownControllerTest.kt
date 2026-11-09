package com.malai.insights.acceptance.happy

import com.malai.insights.acceptance.InsightsAcceptanceTest
import com.malai.insights.model.EntryFact
import com.malai.insights.model.SpendingBreakdownRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class InsightsSpendingBreakdownControllerTest : InsightsAcceptanceTest() {

    private val groceriesEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b1")
    private val anotherGroceriesEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b2")
    private val billsEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b3")
    private val generalEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b4")
    private val transfersEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b5")
    private val incomeEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000b6")

    @Test
    fun sumsDebitsPerCategoryDroppingIncomeTransfersAndCredits() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/spending-breakdown")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SpendingBreakdownRequest(
                    month = "2026-05",
                    entries = listOf(
                        EntryFact(groceriesEntryId, -3_000L, merchantName = "Tesco — weekly shop"),
                        EntryFact(anotherGroceriesEntryId, -1_200L, merchantName = "Tesco — top-up"),
                        EntryFact(billsEntryId, -7_800L, merchantName = "Octopus Energy — DD"),
                        EntryFact(generalEntryId, -1_200L, merchantName = "Corner Shop"),
                        EntryFact(transfersEntryId, -20_000L, merchantName = "Savings sweep"),
                        EntryFact(incomeEntryId, 240_000L, merchantName = "ACME Salary"),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categories" To listOf(
                    json { "category" To "BILLS"; "totalMinorUnits" To 7_800; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 4_200; "transactionCount" To 2; noExtraProps() },
                    json { "category" To "GENERAL"; "totalMinorUnits" To 1_200; "transactionCount" To 1; noExtraProps() },
                )
                "totalMinorUnits" To 13_200
                noExtraProps()
            }
        }
    }

    @Test
    fun emptyMonthYieldsEmptyCategoriesAndZeroTotal() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/spending-breakdown")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SpendingBreakdownRequest(
                    month = "2026-05",
                    entries = emptyList(),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categories" To emptyList<Any>()
                "totalMinorUnits" To 0
                noExtraProps()
            }
        }
    }
}
