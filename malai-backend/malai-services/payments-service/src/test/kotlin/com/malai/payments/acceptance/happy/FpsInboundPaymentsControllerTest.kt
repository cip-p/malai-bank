package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.messages.ReturnReason
import com.malai.payments.support.WithPayments.Companion.FPS_INBOUND_SUSPENSE_ACCOUNT_ID
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class FpsInboundPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val originatorAccountNumber = "12345678"
    private val originatorName = "Bob Marley"

    @Test
    fun knownBeneficiaryReceivesCreditAndAuditEventIsRecorded() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theInboundReturnPublisherIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")))

        val inboundEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000006000")

        and(theStubPublishesFpsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = fromAccountNumber(),
            beneficiaryName = "Alice Walker",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = 5_000L,
            description = "transfer from Bob",
        ))

        whenever(paymentsServiceReceives(
            get()
                .url("/api/v1/payments/${somePaymentUid()}")
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode.value() shouldBe 200
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "SETTLED"
                "rail" To "FPS"
                "direction" To "INBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To inboundEndToEndId.toString()
                "fromSortCode" To malaiSortCode
                "fromAccountNumber" To originatorAccountNumber
                "fromName" To originatorName
                "toAccountId" To someAccountUid().toString()
                "toSortCode" To malaiSortCode
                "toAccountNumber" To fromAccountNumber()
                "toName" To "Alice Walker"
                "amountMinorUnits" To 5_000
                "currency" To "GBP"
                "description" To "transfer from Bob"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.SETTLED
            rail shouldBe Rail.FPS
            direction shouldBe PaymentDirection.INBOUND
            beneficiaryAccountId shouldBe someAccountUid()
            debtorAccountId shouldBe null
            debtorSortCode shouldBe malaiSortCode
            debtorAccountNo shouldBe originatorAccountNumber
            debtorName shouldBe originatorName
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To something
                "type" To "PAYMENT_CREDIT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "description" To "transfer from Bob"
                "entries" To listOf(
                    json {
                        "accountId" To FPS_INBOUND_SUSPENSE_ACCOUNT_ID.toString()
                        "amountMinorUnits" To -5_000
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "transfer from Bob"
                        noExtraProps()
                    },
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To 5_000
                        "counterpartyId" To FPS_INBOUND_SUSPENSE_ACCOUNT_ID.toString()
                        "description" To "transfer from Bob"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INBOUND_PAYMENT_RECEIVED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["resource_type"] shouldBe "Payment"
            this[0]["resource_id"] shouldBe somePaymentUid().toString()
        }

        and(thePublishedInboundReturns()) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun unknownBeneficiaryAutoReturnsAndDoesNotWriteAPaymentRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theInboundReturnPublisherIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())

        val unknownAccountNumber = "99999999"
        and(theDestinationIsExternalAtSortCode(unknownAccountNumber))
        val inboundEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000006100")

        and(theStubPublishesFpsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = unknownAccountNumber,
            beneficiaryName = "Nobody",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = 7_500L,
        ))

        and(thePaymentsInTheDatabase()) {
            this.shouldBeEmpty()
        }

        and(thePublishedInboundReturns()) {
            this shouldHaveSize 1
            this[0].endToEndId shouldBe inboundEndToEndId
            this[0].beneficiarySortCode shouldBe malaiSortCode
            this[0].beneficiaryAccountNumber shouldBe unknownAccountNumber
            this[0].reason shouldBe ReturnReason.INVALID_ACCOUNT
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun duplicateInboundNotifyIsDeduplicatedAndCreditsOnlyOnce() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theFpsSchemeIsReset())
        and(theInboundReturnPublisherIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            someTransferUid(),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-10-15T10:30:00Z")))

        val inboundEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000006200")

        and(theStubPublishesFpsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = fromAccountNumber(),
            beneficiaryName = "Alice Walker",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = 2_000L,
        ))
        and(theStubPublishesFpsInbound(
            endToEndId = inboundEndToEndId,
            beneficiarySortCode = malaiSortCode,
            beneficiaryAccountNumber = fromAccountNumber(),
            beneficiaryName = "Alice Walker",
            originatorSortCode = malaiSortCode,
            originatorAccountNumber = originatorAccountNumber,
            originatorName = originatorName,
            amountMinorUnits = 2_000L,
        ))

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.count { it["action"] == "INBOUND_PAYMENT_RECEIVED" } shouldBe 1
        }
    }
}
