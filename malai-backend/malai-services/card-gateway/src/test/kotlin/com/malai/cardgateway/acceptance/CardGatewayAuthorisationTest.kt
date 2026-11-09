package com.malai.cardgateway.acceptance

import com.malai.client.malai.card.builders.response.AuthoriseCardResponseBuilder.Companion.anApprovedAuthorisation
import com.malai.client.malai.card.builders.response.AuthoriseCardResponseBuilder.Companion.aDeclinedAuthorisation
import com.malai.scheme.contracts.card.CardSchemeAmount
import com.malai.scheme.contracts.card.CardSchemeAuthorisationRequest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.OK
import java.util.UUID

class CardGatewayAuthorisationTest : CardGatewayAcceptanceTest() {

    private val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c1")
    private val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")

    @Test
    fun approvedDecisionIsReturnedAsAnApprovedSchemeResponse() = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(authoriseCardReturns(anApprovedAuthorisation()
            .withAuthorisationId(authorisationId)
            .withDecision("APPROVED")
            .withDeclineReason(null)))

        whenever(cardGatewayReceives(anAuthorisationWebhook()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference.toString()
                "outcome" To "APPROVED"
                noExtraProps()
            }
        }
        and(cardGatewayMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
            body containsProp json {
                "schemeReference" To schemeReference.toString()
                "pan" To "9904320000012341"
                "amountMinorUnits" To 4_370
                "currency" To "GBP"
                "merchantName" To "Fictional Coffee Co"
                "mcc" To "5814"
                noExtraProps()
            }
        }
    }

    @Test
    fun frozenDeclineIsReturnedWithSchemeCode62() = assertDeclineMapsToSchemeCode("CARD_FROZEN", "62")

    @Test
    fun cancelledDeclineIsReturnedWithSchemeCode04() = assertDeclineMapsToSchemeCode("CARD_CANCELLED", "04")

    @Test
    fun insufficientFundsDeclineIsReturnedWithSchemeCode51() = assertDeclineMapsToSchemeCode("INSUFFICIENT_FUNDS", "51")

    @Test
    fun overLimitDeclineIsReturnedWithSchemeCode61() = assertDeclineMapsToSchemeCode("OVER_LIMIT", "61")

    @Test
    fun unknownCardDeclineIsReturnedWithSchemeCode14() = assertDeclineMapsToSchemeCode("UNKNOWN_CARD", "14")

    private fun assertDeclineMapsToSchemeCode(declineReason: String, schemeCode: String) = malaiAcceptanceTest {
        given(oauth2ServiceTokenEndpointReturnsToken())
        and(authoriseCardReturns(aDeclinedAuthorisation()
            .withAuthorisationId(authorisationId)
            .withDecision("DECLINED")
            .withDeclineReason(declineReason)))

        whenever(cardGatewayReceives(anAuthorisationWebhook()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "schemeReference" To schemeReference.toString()
                "outcome" To "DECLINED"
                "declineReasonCode" To schemeCode
                noExtraProps()
            }
        }
        and(cardGatewayMadeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations"
        }) {
            method shouldBe POST
            headers["Authorization"]!!.startsWith("Bearer ") shouldBe true
        }
    }

    private fun anAuthorisationWebhook() =
        post()
            .url("/cards/callbacks/authorise")
            .header("Content-Type", "application/json")
            .body(testingObjectMapper.writeValueAsString(CardSchemeAuthorisationRequest(
                schemeReference = schemeReference,
                pan = "9904320000012341",
                amount = CardSchemeAmount(4_370, "GBP"),
                merchantName = "Fictional Coffee Co",
                mcc = "5814",
            )))
}
