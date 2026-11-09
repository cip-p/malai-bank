package com.malai.fpsgateway.acceptance

import com.malai.payments.messages.FpsOutboundSubmit
import com.malai.payments.messages.ReturnReason
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class FpsGatewayOutboundTest : FpsGatewayAcceptanceTest() {

    @Test
    fun submitCommandIsMappedToASchemeShapedHttpSubmit() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsSchemeWillAcceptSubmissions())

        and(paymentsServiceSubmits(aFpsSubmit(paymentId, endToEndId, "60-00-01", "12345678")))

        and(fpsGatewayMadeTheFollowingHttpRequest { url == "$FPS_SCHEME_PATH/fps/payments" }) {
            method shouldBe POST
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "paymentId" To paymentId.toString()
                "debtor" To json {
                    "sortCode" To "65-82-37"
                    "accountNumber" To "10000001"
                    "name" To "Alice"
                    noExtraProps()
                }
                "creditor" To json {
                    "sortCode" To "60-00-01"
                    "accountNumber" To "12345678"
                    "name" To "Bob"
                    noExtraProps()
                }
                "instructedAmount" To json {
                    "minorUnits" To 1_500
                    "currency" To "GBP"
                    noExtraProps()
                }
                "remittanceInformation" To "to my friend"
                "copVerdictOverride" To false
                noExtraProps()
            }
        }
    }

    @Test
    fun settlementCallbackProducesTheSettledEvent() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsSchemeWillAcceptSubmissions())
        and(paymentsServiceSubmits(aFpsSubmit(paymentId, endToEndId, "60-00-01", "12345678")))

        whenever(theFpsSchemeFiresSettlementCallback(paymentId, endToEndId))

        then(theProducedSettledEvents()) {
            this shouldHaveSize 1
            this.single().paymentId shouldBe paymentId
            this.single().endToEndId shouldBe endToEndId
        }
    }

    @Test
    fun returnCallbackProducesTheReturnedEventWithTranslatedReason() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theFpsSchemeWillAcceptSubmissions())
        and(paymentsServiceSubmits(aFpsSubmit(paymentId, endToEndId, "60-00-01", "99000001")))

        whenever(theFpsSchemeFiresReturnCallback(paymentId, endToEndId, reasonCode = "AC04"))

        then(theProducedReturnedEvents()) {
            this shouldHaveSize 1
            this.single().paymentId shouldBe paymentId
            this.single().endToEndId shouldBe endToEndId
            this.single().reason shouldBe ReturnReason.ACCOUNT_CLOSED
        }
    }

    private fun aFpsSubmit(
        paymentId: UUID,
        endToEndId: UUID,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
    ) = FpsOutboundSubmit(
        paymentId = paymentId,
        endToEndId = endToEndId,
        debtorSortCode = "65-82-37",
        debtorAccountNumber = "10000001",
        debtorName = "Alice",
        beneficiarySortCode = beneficiarySortCode,
        beneficiaryAccountNumber = beneficiaryAccountNumber,
        beneficiaryName = "Bob",
        amountMinorUnits = 1_500L,
        currency = "GBP",
        description = "to my friend",
    )
}
