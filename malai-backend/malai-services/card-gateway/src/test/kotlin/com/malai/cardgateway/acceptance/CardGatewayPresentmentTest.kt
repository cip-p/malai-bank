package com.malai.cardgateway.acceptance

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardGatewayPresentmentTest : CardGatewayAcceptanceTest() {

    @Test
    fun presentmentWebhookIsTranslatedToAPresentedEventOnTheQueue() = malaiAcceptanceTest {
        val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c1")

        whenever(cardGatewayReceives(
            post()
                .url("/cards/callbacks/present")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "schemeReference": "$schemeReference",
                      "amount": { "minorUnits": 4370, "currency": "GBP" },
                      "merchantName": "Fictional Coffee Co"
                    }
                    """.trimIndent()
                )
        ))

        then(theProducedPresentmentEvents()) {
            this shouldHaveSize 1
            this.single().schemeReference shouldBe schemeReference
            this.single().presentedAmountMinorUnits shouldBe 4370L
            this.single().currency shouldBe "GBP"
            this.single().merchantName shouldBe "Fictional Coffee Co"
        }
    }
}
