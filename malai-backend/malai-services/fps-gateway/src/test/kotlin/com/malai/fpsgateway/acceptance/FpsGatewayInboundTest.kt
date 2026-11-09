package com.malai.fpsgateway.acceptance

import com.malai.payments.messages.FpsInboundReturned
import com.malai.payments.messages.ReturnReason
import com.malai.scheme.contracts.fps.FpsSchemeAmount
import com.malai.scheme.contracts.fps.FpsSchemeInboundNotification
import com.malai.scheme.contracts.fps.FpsSchemeParty
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class FpsGatewayInboundTest : FpsGatewayAcceptanceTest() {

    @Test
    fun inboundWebhookIsMappedToAReceivedEvent() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        whenever(theFpsSchemeNotifiesInbound(anInboundNotification(endToEndId, "60-00-01", "12345678")))

        then(theProducedReceivedEvents()) {
            this shouldHaveSize 1
            this.single().endToEndId shouldBe endToEndId
            this.single().beneficiarySortCode shouldBe "60-00-01"
            this.single().beneficiaryAccountNumber shouldBe "12345678"
            this.single().beneficiaryName shouldBe "Alice Walker"
            this.single().originatorSortCode shouldBe "65-82-37"
            this.single().originatorAccountNumber shouldBe "10000001"
            this.single().originatorName shouldBe "Bob Marley"
            this.single().amountMinorUnits shouldBe 5_000L
            this.single().currency shouldBe "GBP"
            this.single().description shouldBe "salary"
        }
    }

    @Test
    fun returnCommandIsMappedToASchemeShapedReturnToSender() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        given(theFpsSchemeWillAcceptReturns())

        and(paymentsServiceCommandsReturn(
            FpsInboundReturned(
                endToEndId = endToEndId,
                beneficiarySortCode = "60-00-01",
                beneficiaryAccountNumber = "00000000",
                reason = ReturnReason.INVALID_ACCOUNT,
            ),
        ))

        and(fpsGatewayMadeTheFollowingHttpRequest { url == "$FPS_SCHEME_PATH/fps/returns" }) {
            method shouldBe POST
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "creditor" To json {
                    "sortCode" To "60-00-01"
                    "accountNumber" To "00000000"
                    "name" To null
                    noExtraProps()
                }
                "reasonCode" To "AC01"
                noExtraProps()
            }
        }
    }

    private fun anInboundNotification(
        endToEndId: UUID,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
    ) = FpsSchemeInboundNotification(
        endToEndId = endToEndId,
        creditor = FpsSchemeParty(beneficiarySortCode, beneficiaryAccountNumber, "Alice Walker"),
        debtor = FpsSchemeParty("65-82-37", "10000001", "Bob Marley"),
        instructedAmount = FpsSchemeAmount(5_000L, "GBP"),
        remittanceInformation = "salary",
    )
}
