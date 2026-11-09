package com.malai.bacsgateway.acceptance

import com.malai.scheme.contracts.bacs.BacsSchemeAmount
import com.malai.scheme.contracts.bacs.BacsSchemeDirectCredit
import com.malai.scheme.contracts.bacs.BacsSchemeParty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class BacsGatewayInboundTest : BacsGatewayAcceptanceTest() {

    @Test
    fun inboundWebhookIsMappedToAReceivedEvent() = malaiAcceptanceTest {
        val endToEndId = UUID.randomUUID()

        whenever(theBacsSchemeNotifiesInbound(aDirectCredit(endToEndId, "60-00-01", "12345678")))

        then(theProducedReceivedEvents()) {
            this shouldHaveSize 1
            this.single().endToEndId shouldBe endToEndId
            this.single().beneficiarySortCode shouldBe "60-00-01"
            this.single().beneficiaryAccountNumber shouldBe "12345678"
            this.single().beneficiaryName shouldBe "Alice Walker"
            this.single().originatorSortCode shouldBe "20-00-05"
            this.single().originatorAccountNumber shouldBe "20000020"
            this.single().originatorName shouldBe "ACME Employer Ltd"
            this.single().amountMinorUnits shouldBe 250_000L
            this.single().currency shouldBe "GBP"
            this.single().description shouldBe "Salary"
        }
    }

    private fun aDirectCredit(
        endToEndId: UUID,
        beneficiarySortCode: String,
        beneficiaryAccountNumber: String,
    ) = BacsSchemeDirectCredit(
        endToEndId = endToEndId,
        creditor = BacsSchemeParty(beneficiarySortCode, beneficiaryAccountNumber, "Alice Walker"),
        originator = BacsSchemeParty("20-00-05", "20000020", "ACME Employer Ltd"),
        instructedAmount = BacsSchemeAmount(250_000L, "GBP"),
        remittanceInformation = "Salary",
    )
}
