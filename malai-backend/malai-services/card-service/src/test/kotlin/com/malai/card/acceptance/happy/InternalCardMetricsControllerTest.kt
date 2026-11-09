package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.card.model.CardStatus.ACTIVE
import com.malai.card.model.CardStatus.CANCELLED
import com.malai.card.model.CardVariant.CREDIT
import com.malai.card.model.CardVariant.DEBIT
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.util.UUID

class InternalCardMetricsControllerTest : CardAcceptanceTest() {

    private val firstAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a1")
    private val secondAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a2")
    private val thirdAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a3")
    private val fourthAccountId = UUID.fromString("00000000-0000-4000-6000-0000000000a4")
    private val firstCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b1")
    private val secondCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b2")
    private val thirdCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b3")
    private val fourthCardId = UUID.fromString("00000000-0000-4000-6000-0000000000b4")

    @Test
    fun countsBothVariantsIncludingOneCancelledTheSameDayAndExcludesOneIssuedBeforeTheWindow() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(cardId = firstCardId, accountId = firstAccountId, accountHolderId = someAccountHolderUid(),
            variant = DEBIT, status = ACTIVE, createdAt = Instant.parse("2026-07-20T09:00:00Z")))
        and(aCardExists(cardId = secondCardId, accountId = secondAccountId, accountHolderId = someAccountHolderUid(),
            variant = CREDIT, status = ACTIVE, createdAt = Instant.parse("2026-07-20T10:00:00Z")))
        and(aCardExists(cardId = thirdCardId, accountId = thirdAccountId, accountHolderId = someAccountHolderUid(),
            variant = DEBIT, status = CANCELLED, createdAt = Instant.parse("2026-07-20T11:00:00Z")))
        and(aCardExists(cardId = fourthCardId, accountId = fourthAccountId, accountHolderId = someAccountHolderUid(),
            variant = DEBIT, status = ACTIVE, createdAt = Instant.parse("2026-07-19T22:59:59Z")))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/issued-count")
                .queryParam("from", "2026-07-19T23:00:00Z")
                .queryParam("until", "2026-07-20T23:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 3
                noExtraProps()
            }
        }
    }

    @Test
    fun reportsZeroForAWindowWithNoIssuance() = malaiAcceptanceTest {
        given(thereAreNoCards())

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/issued-count")
                .queryParam("from", "2026-07-19T23:00:00Z")
                .queryParam("until", "2026-07-20T23:00:00Z")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "count" To 0
                noExtraProps()
            }
        }
    }
}
