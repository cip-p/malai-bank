package com.malai.payments.acceptance.sad

import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.messages.ReturnReason
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class BacsCollectionPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val creditorSortCode = "65-82-01"
    private val creditorAccountNumber = "22334455"
    private val creditorName = "Octopus Energy Ltd"

    @Test
    fun collectionExceedingAvailableFundsIsReturnedUnpaidWithoutPostingToTheLedger() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theBacsCollectionReturnPublisherIsReset())
        and(nowIs("2026-05-25T09:00:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturnsInsufficientFunds())

        val collectionEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000009200")

        and(theStubPublishesBacsCollection(
            endToEndId = collectionEndToEndId,
            debtorSortCode = malaiSortCode,
            debtorAccountNumber = fromAccountNumber(),
            debtorName = "Alice Walker",
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = 900_000L,
            description = "Energy DD",
        ))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.RETURNED
            rail shouldBe Rail.BACS_DIRECT_DEBIT
            direction shouldBe PaymentDirection.INBOUND
            returnReason shouldBe ReturnReason.INSUFFICIENT_FUNDS
            debtorAccountId shouldBe someAccountUid()
            beneficiarySortCode shouldBe creditorSortCode
        }

        and(thePublishedCollectionReturns()) {
            this shouldHaveSize 1
            this[0].endToEndId shouldBe collectionEndToEndId
            this[0].reason shouldBe ReturnReason.INSUFFICIENT_FUNDS
            this[0].creditorSortCode shouldBe creditorSortCode
            this[0].creditorAccountNumber shouldBe creditorAccountNumber
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INBOUND_COLLECTION_RETURNED"
        }
    }

    @Test
    fun collectionForUnknownTargetAccountIsReturnedAndDoesNotWriteAPaymentRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theBacsCollectionReturnPublisherIsReset())
        and(nowIs("2026-05-25T09:00:00Z"))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())

        val unknownAccountNumber = "99999997"
        and(theDestinationIsExternalAtSortCode(unknownAccountNumber))
        val collectionEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000009300")

        and(theStubPublishesBacsCollection(
            endToEndId = collectionEndToEndId,
            debtorSortCode = malaiSortCode,
            debtorAccountNumber = unknownAccountNumber,
            debtorName = "Nobody",
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = 4_200L,
        ))

        and(thePaymentsInTheDatabase()) {
            this.shouldBeEmpty()
        }

        and(thePublishedCollectionReturns()) {
            this shouldHaveSize 1
            this[0].endToEndId shouldBe collectionEndToEndId
            this[0].reason shouldBe ReturnReason.INVALID_ACCOUNT
            this[0].creditorAccountNumber shouldBe creditorAccountNumber
        }
    }
}
