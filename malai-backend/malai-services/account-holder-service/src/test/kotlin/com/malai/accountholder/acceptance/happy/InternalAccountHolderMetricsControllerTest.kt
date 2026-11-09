package com.malai.accountholder.acceptance.happy

import com.malai.accountholder.acceptance.AccountHolderAcceptanceTest
import com.malai.accountholder.entity.AccountHolderType.BUSINESS
import com.malai.accountholder.entity.AccountHolderType.INDIVIDUAL
import com.malai.accountholder.entity.AccountHolderType.JOINT
import com.malai.accountholder.entity.AccountHolderType.SOLE_TRADER
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalAccountHolderMetricsControllerTest : AccountHolderAcceptanceTest() {

    private val aSoleTraderAccountHolderUid = UUID.fromString("00000000-0000-4000-8000-0000000000b3")
    private val aJointAccountHolderUid = UUID.fromString("00000000-0000-4000-8000-0000000000b4")

    private val windowStart = "2026-07-19T23:00:00Z"
    private val windowEnd = "2026-07-20T23:00:00Z"

    @BeforeEach
    fun startFromAnEmptyBank() = cleanTables()

    @Test
    fun everyAccountHolderTypeCounts() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-20T09:00:00Z")))
        and(anAccountHolderExists(anotherAccountHolderUid(), BUSINESS, createdAt = Instant.parse("2026-07-20T10:00:00Z")))
        and(anAccountHolderExists(aSoleTraderAccountHolderUid, SOLE_TRADER, createdAt = Instant.parse("2026-07-20T11:00:00Z")))
        and(anAccountHolderExists(aJointAccountHolderUid, JOINT, createdAt = Instant.parse("2026-07-20T12:00:00Z")))

        whenever(accountHolderServiceReceives(createdCountBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 4
                noExtraProps()
            }
        }
    }

    @Test
    fun aHolderCreatedJustEitherSideOfTheWindowFallsOnTheCorrectSide() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-19T22:59:59Z")))
        and(anAccountHolderExists(anotherAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-19T23:00:00Z")))

        whenever(accountHolderServiceReceives(createdCountBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 1
                noExtraProps()
            }
        }
    }

    @Test
    fun theWindowEndIsExclusiveSoTheNextDaysFirstHolderIsNotCounted() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-20T22:59:59Z")))
        and(anAccountHolderExists(anotherAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-20T23:00:00Z")))

        whenever(accountHolderServiceReceives(createdCountBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 1
                noExtraProps()
            }
        }
    }

    @Test
    fun theCountIsOfHoldersNotUsersSoOneUsersTwoHoldersCountTwiceAndAHolderlessUserCountsNotAtAll() =
        malaiAcceptanceTest {
            given(anAccountHolderExists(
                someAccountHolderUid(),
                INDIVIDUAL,
                userId = someUserId(),
                createdAt = Instant.parse("2026-07-20T09:00:00Z"),
            ))
            and(anAccountHolderExists(
                anotherAccountHolderUid(),
                BUSINESS,
                userId = someUserId(),
                createdAt = Instant.parse("2026-07-20T10:00:00Z"),
            ))

            whenever(accountHolderServiceReceives(createdCountBetween(windowStart, windowEnd)))

            then(theLastResponseHas()) {
                statusCode shouldBe OK
                body containsProp json {
                    "count" To 2
                    noExtraProps()
                }
            }
        }

    @Test
    fun aQuietWindowCountsZeroRatherThanFailing() = malaiAcceptanceTest {
        given(anAccountHolderExists(someAccountHolderUid(), INDIVIDUAL, createdAt = Instant.parse("2026-07-18T09:00:00Z")))

        whenever(accountHolderServiceReceives(createdCountBetween(windowStart, windowEnd)))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 0
                noExtraProps()
            }
        }
    }

    private fun createdCountBetween(from: String, until: String) =
        get()
            .url("/api/v1/internal/account-holders/created-count")
            .queryParam("from", from)
            .queryParam("until", until)
            .withServiceJwtToken()
}
