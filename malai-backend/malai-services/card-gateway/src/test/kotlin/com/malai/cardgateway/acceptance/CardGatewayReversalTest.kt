package com.malai.cardgateway.acceptance

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CardGatewayReversalTest : CardGatewayAcceptanceTest() {

    @Test
    fun reversalWebhookIsTranslatedToAReversedEventOnTheQueue() = malaiAcceptanceTest {
        val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000c2")

        whenever(cardGatewayReceives(
            post()
                .url("/cards/callbacks/reverse")
                .header("Content-Type", "application/json")
                .body(
                    """
                    {
                      "schemeReference": "$schemeReference"
                    }
                    """.trimIndent()
                )
        ))

        then(theProducedReversalEvents()) {
            this shouldHaveSize 1
            this.single().schemeReference shouldBe schemeReference
        }
    }
}
