package com.malai.bacsgateway.acceptance

import com.malai.payments.messages.BacsCollectionReturn
import com.malai.payments.messages.ReturnReason
import com.malai.scheme.contracts.bacs.BacsSchemeAmount
import com.malai.scheme.contracts.bacs.BacsSchemeDirectDebitCollection
import com.malai.scheme.contracts.bacs.BacsSchemeParty
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class BacsGatewayCollectionTest : BacsGatewayAcceptanceTest() {

    private val debtorSortCode = "65-82-37"
    private val debtorAccountNumber = "10000001"
    private val creditorSortCode = "60-00-01"
    private val creditorAccountNumber = "22334455"
    private val creditorName = "Octopus Energy Ltd"

    @Test
    fun collectionOriginationIsMappedToACollectionEvent() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        whenever(theBacsSchemeOriginatesCollection(aCollection(endToEndId)))

        then(theProducedCollectionEvents()) {
            this shouldHaveSize 1
            this.single().endToEndId shouldBe endToEndId
            this.single().debtorSortCode shouldBe debtorSortCode
            this.single().debtorAccountNumber shouldBe debtorAccountNumber
            this.single().debtorName shouldBe "Alice Walker"
            this.single().creditorSortCode shouldBe creditorSortCode
            this.single().creditorAccountNumber shouldBe creditorAccountNumber
            this.single().creditorName shouldBe creditorName
            this.single().amountMinorUnits shouldBe 4_200L
            this.single().currency shouldBe "GBP"
            this.single().description shouldBe "Energy DD"
        }
    }

    @Test
    fun collectionReturnCommandIsMappedToASchemeShapedReturn() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        given(theBacsSchemeWillAcceptReturns())

        and(paymentsServiceCommandsCollectionReturn(
            BacsCollectionReturn(
                endToEndId = endToEndId,
                creditorSortCode = creditorSortCode,
                creditorAccountNumber = creditorAccountNumber,
                creditorName = creditorName,
                reason = ReturnReason.INSUFFICIENT_FUNDS,
            ),
        ))

        and(bacsGatewayMadeTheFollowingHttpRequest {
            url == "$BACS_SCHEME_PATH/bacs/collections/returns"
        }) {
            method shouldBe POST
            body containsProp json {
                "endToEndId" To endToEndId.toString()
                "creditor" To json {
                    "sortCode" To creditorSortCode
                    "accountNumber" To creditorAccountNumber
                    "name" To creditorName
                    noExtraProps()
                }
                "reasonCode" To "0"
                noExtraProps()
            }
        }
    }

    private fun aCollection(endToEndId: UUID) = BacsSchemeDirectDebitCollection(
        endToEndId = endToEndId,
        creditor = BacsSchemeParty(creditorSortCode, creditorAccountNumber, creditorName),
        debtor = BacsSchemeParty(debtorSortCode, debtorAccountNumber, "Alice Walker"),
        instructedAmount = BacsSchemeAmount(4_200L, "GBP"),
        remittanceInformation = "Energy DD",
    )
}
