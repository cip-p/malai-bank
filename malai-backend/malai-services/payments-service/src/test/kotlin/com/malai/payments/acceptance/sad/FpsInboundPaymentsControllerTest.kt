package com.malai.payments.acceptance.sad

import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.messages.ReturnReason
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class FpsInboundPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun inboundForUnknownBeneficiaryAtMalaiAutoReturnsWithoutTouchingTheLedger() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theInboundReturnPublisherIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())

        val unknownAccountNumber = "99999998"
        and(theDestinationIsExternalAtSortCode(unknownAccountNumber))
        val inboundEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000006300")

        and(theStubPublishesFpsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = unknownAccountNumber,
            beneficiaryName = "Nobody",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = "12345678",
            originatorName = "Bob Marley",
            amountMinorUnits = 4_200L,
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
