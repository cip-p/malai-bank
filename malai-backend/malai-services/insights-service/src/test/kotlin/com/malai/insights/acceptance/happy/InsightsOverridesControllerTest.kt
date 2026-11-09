package com.malai.insights.acceptance.happy

import com.malai.insights.acceptance.InsightsAcceptanceTest
import com.malai.insights.entity.CategoryOverrideEntity
import com.malai.insights.model.Category.BILLS
import com.malai.insights.model.Category.EATING_OUT
import com.malai.insights.model.Category.GROCERIES
import com.malai.insights.model.CategoriseEntriesRequest
import com.malai.insights.model.CategoryOverrideRequest
import com.malai.insights.model.EntryFact
import com.malai.insights.model.SpendingBreakdownRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.delete
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.put
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InsightsOverridesControllerTest : InsightsAcceptanceTest() {

    private val someEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000d1")
    private val anotherEntryId = UUID.fromString("00000000-0000-4000-7000-0000000000d2")

    @Test
    fun putStoresAnOverrideKeyedByEntryId() = malaiAcceptanceTest {
        given(nowIs("2026-06-01T10:00:00Z"))

        whenever(insightsServiceReceives(
            put()
                .url("/api/v1/internal/insights/overrides/$someEntryId")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoryOverrideRequest(category = GROCERIES)))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
        and(theCategoryOverrideInTheDatabase(someEntryId)) {
            this shouldBe CategoryOverrideEntity(
                entryId = someEntryId,
                category = GROCERIES,
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
                createdBy = null,
            )
        }
    }

    @Test
    fun replayingPutWithTheSameBodyYieldsTheSameState() = malaiAcceptanceTest {
        given(nowIs("2026-06-01T10:00:00Z"))
        and(aCategoryOverrideExists(
            someEntryId,
            GROCERIES,
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
        ))

        whenever(insightsServiceReceives(
            put()
                .url("/api/v1/internal/insights/overrides/$someEntryId")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoryOverrideRequest(category = GROCERIES)))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
        and(theCategoryOverrideInTheDatabase(someEntryId)) {
            this shouldBe CategoryOverrideEntity(
                entryId = someEntryId,
                category = GROCERIES,
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
                createdBy = null,
            )
        }
    }

    @Test
    fun rePuttingChangesTheCategoryAndStampsUpdatedAtButPreservesCreatedAt() = malaiAcceptanceTest {
        given(aCategoryOverrideExists(
            someEntryId,
            GROCERIES,
            createdAt = Instant.parse("2026-06-01T10:00:00Z"),
            updatedAt = Instant.parse("2026-06-01T10:00:00Z"),
        ))
        and(nowIs("2026-06-02T15:30:00Z"))

        whenever(insightsServiceReceives(
            put()
                .url("/api/v1/internal/insights/overrides/$someEntryId")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoryOverrideRequest(category = EATING_OUT)))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
        and(theCategoryOverrideInTheDatabase(someEntryId)) {
            this shouldBe CategoryOverrideEntity(
                entryId = someEntryId,
                category = EATING_OUT,
                createdAt = Instant.parse("2026-06-01T10:00:00Z"),
                updatedAt = Instant.parse("2026-06-02T15:30:00Z"),
                createdBy = null,
            )
        }
    }

    @Test
    fun deleteRemovesTheOverride() = malaiAcceptanceTest {
        given(aCategoryOverrideExists(someEntryId, GROCERIES))

        whenever(insightsServiceReceives(
            delete().url("/api/v1/internal/insights/overrides/$someEntryId")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
        and(theCategoryOverrideInTheDatabase(someEntryId)) {
            this shouldBe null
        }
    }

    @Test
    fun deleteOnAnEntryWithNoOverrideIsANoOp() = malaiAcceptanceTest {
        whenever(insightsServiceReceives(
            delete().url("/api/v1/internal/insights/overrides/$someEntryId")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }
        and(theCategoryOverrideInTheDatabase(someEntryId)) {
            this shouldBe null
        }
    }

    @Test
    fun overrideWinsOverTheRuleOnTheCategorisationsEndpoint() = malaiAcceptanceTest {
        given(aCategoryOverrideExists(someEntryId, EATING_OUT))

        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/categorisations")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(CategoriseEntriesRequest(entries = listOf(
                    EntryFact(someEntryId, -3_000L, merchantName = "Tesco — weekly shop"),
                    EntryFact(anotherEntryId, -1_200L, merchantName = "Tesco — top-up"),
                ))))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categorisations" To listOf(
                    json { "entryId" To someEntryId.toString(); "category" To "EATING_OUT"; noExtraProps() },
                    json { "entryId" To anotherEntryId.toString(); "category" To "GROCERIES"; noExtraProps() },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun overrideMovesAnEntryBetweenSlicesOnTheSpendingBreakdownEndpoint() = malaiAcceptanceTest {
        given(aCategoryOverrideExists(someEntryId, BILLS))

        whenever(insightsServiceReceives(
            post()
                .url("/api/v1/internal/insights/spending-breakdown")
                .header("Content-Type", "application/json")
                .body(testingObjectMapper.writeValueAsString(SpendingBreakdownRequest(
                    month = "2026-05",
                    entries = listOf(
                        EntryFact(someEntryId, -3_000L, merchantName = "Tesco — weekly shop"),
                        EntryFact(anotherEntryId, -1_200L, merchantName = "Tesco — top-up"),
                    ),
                )))
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "categories" To listOf(
                    json { "category" To "BILLS"; "totalMinorUnits" To 3_000; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 1_200; "transactionCount" To 1; noExtraProps() },
                )
                "totalMinorUnits" To 4_200
                noExtraProps()
            }
        }
    }
}
