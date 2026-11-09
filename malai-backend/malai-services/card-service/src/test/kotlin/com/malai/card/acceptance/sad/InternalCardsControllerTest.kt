package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND

class InternalCardsControllerTest : CardAcceptanceTest() {

    @Test
    fun resolveForAnUnknownCardIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoCards())

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/${someCardUid()}")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    @Test
    fun resolveForACardBelongingToAnotherHolderIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/${someCardUid()}")
                .queryParam("accountHolderId", someAccountHolderUid())
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }
}
