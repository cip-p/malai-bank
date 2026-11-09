package com.malai.card.acceptance.sad

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.NOT_FOUND

class CardsControllerTest : CardAcceptanceTest() {

    @Test
    fun detailForAnUnknownCardIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoCards())

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/cards/${someCardUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }

    @Test
    fun detailForACardBelongingToAnotherHolderIsNotFound() = malaiAcceptanceTest {
        given(thereAreNoCards())
        and(aCardExists(
            cardId = someCardUid(),
            accountId = someAccountUid(),
            accountHolderId = anotherAccountHolderUid(),
        ))

        whenever(cardServiceReceives(
            get()
                .url("/api/v1/cards/${someCardUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NOT_FOUND
            body containsProp json {
                "message" To "8993"
            }
        }
    }
}
