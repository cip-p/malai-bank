package com.malai.swiftgateway.acceptance

import com.malai.payments.messages.SwiftOutboundSubmit
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class SwiftGatewayOutboundTest : SwiftGatewayAcceptanceTest() {

    @Test
    fun submitCommandIsMappedToASchemeShapedHttpSubmit() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theSwiftSchemeWillAcceptSubmissions())

        and(paymentsServiceSubmits(aSwiftSubmit(paymentId, endToEndId)))

        and(swiftGatewayMadeTheFollowingHttpRequest { url == "$SWIFT_SCHEME_PATH/swift/payments" }) {
            method shouldBe POST
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "paymentId" To paymentId.toString()
                "debtor" To json {
                    "iban" To "GB82MALA65823710000001"
                    "bic" To "MALAGB22XXX"
                    "name" To "Alice Walker"
                    noExtraProps()
                }
                "creditor" To json {
                    "iban" To "DE89370400440532013000"
                    "bic" To "COBADEFFXXX"
                    "name" To "Helga Mueller"
                    noExtraProps()
                }
                "instructedAmount" To json {
                    "minorUnits" To 10_000
                    "currency" To "GBP"
                    noExtraProps()
                }
                "settlementAmount" To json {
                    "minorUnits" To 11_500
                    "currency" To "EUR"
                    noExtraProps()
                }
                "paymentPurpose" To "Rent"
                "remittanceInformation" To "rent in Berlin"
                noExtraProps()
            }
        }
    }

    @Test
    fun settlementCallbackProducesTheSettledEvent() = malaiAcceptanceTest {
        val paymentId = UUID.randomUUID()
        val endToEndId = UUID.randomUUID()

        given(theSwiftSchemeWillAcceptSubmissions())
        and(paymentsServiceSubmits(aSwiftSubmit(paymentId, endToEndId)))

        whenever(theSwiftSchemeFiresSettlementCallback(paymentId, endToEndId))

        then(theProducedSettledEvents()) {
            this shouldHaveSize 1
            this.single().paymentId shouldBe paymentId
            this.single().endToEndId shouldBe endToEndId
        }
    }

    private fun aSwiftSubmit(paymentId: UUID, endToEndId: UUID) = SwiftOutboundSubmit(
        paymentId = paymentId,
        endToEndId = endToEndId,
        debtorIban = "GB82MALA65823710000001",
        debtorBic = "MALAGB22XXX",
        debtorName = "Alice Walker",
        beneficiaryIban = "DE89370400440532013000",
        beneficiaryBic = "COBADEFFXXX",
        beneficiaryName = "Helga Mueller",
        paymentPurpose = "Rent",
        amountMinorUnits = 10_000L,
        currency = "GBP",
        targetAmountMinorUnits = 11_500L,
        targetCurrency = "EUR",
        description = "rent in Berlin",
    )
}
