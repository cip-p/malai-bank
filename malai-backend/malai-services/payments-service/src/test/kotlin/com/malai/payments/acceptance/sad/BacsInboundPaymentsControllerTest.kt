package com.malai.payments.acceptance.sad

import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.messages.ReturnReason
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class BacsInboundPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun bacsInboundForUnknownBeneficiaryAutoReturnsAndDoesNotTouchTheLedger() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theInboundReturnPublisherIsReset())
        and(nowIs("2026-05-25T09:00:00Z"))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())

        val unknownAccountNumber = "99999998"
        and(theDestinationIsExternalAtSortCode(unknownAccountNumber))
        val inboundEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000008400")

        and(theStubPublishesBacsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = unknownAccountNumber,
            beneficiaryName = "Nobody",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = "20000020",
            originatorName = "ACME Employer Ltd",
            amountMinorUnits = 250_000L,
        ))

        and(thePaymentsInTheDatabase()) {
            this.shouldBeEmpty()
        }

        and(thePublishedInboundReturns()) {
            this shouldHaveSize 1
            this[0].reason shouldBe ReturnReason.INVALID_ACCOUNT
            this[0].beneficiaryAccountNumber shouldBe unknownAccountNumber
        }
    }
}
