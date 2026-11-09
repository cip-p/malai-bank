package com.malai.cardgateway.acceptance

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardGatewayRefundTest : CardGatewayAcceptanceTest() {

    @Test
    fun refundWebhookIsTranslatedToARefundedEventOnTheQueue() = malaiAcceptanceTest {
        val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c3")
        val refundReference = UUID.fromString("00000000-0000-4000-9000-0000000000c3")

        whenever(cardGatewayReceives(
            post()
                .url("/cards/callbacks/refund")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "schemeReference": "$schemeReference",
                      "refundReference": "$refundReference",
                      "amount": { "minorUnits": 4370, "currency": "GBP" }
                    }
                    """.trimIndent()
                )
        ))

        then(theProducedRefundEvents()) {
            this shouldHaveSize 1
            this.single().schemeReference shouldBe schemeReference
            this.single().refundReference shouldBe refundReference
            this.single().refundAmountMinorUnits shouldBe 4370L
            this.single().currency shouldBe "GBP"
        }
    }
}
