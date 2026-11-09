package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentDirection
import com.malai.payments.entity.PaymentState
import com.malai.payments.entity.Rail
import com.malai.payments.support.WithPayments.Companion.BACS_INBOUND_SUSPENSE_ACCOUNT_ID
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.util.UUID

class BacsCollectionPaymentsControllerTest : PaymentsAcceptanceTest() {

    private val creditorSortCode = "65-82-01"
    private val creditorAccountNumber = "22334455"
    private val creditorName = "Octopus Energy Ltd"

    @Test
    fun collectionWithSufficientFundsDebitsDebtorAndRecordsSettledBacsDirectDebitPayment() = malaiAcceptanceTest {
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
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-05-25T09:00:00Z")))

        val collectionEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000009000")

        and(theStubPublishesBacsCollection(
            endToEndId = collectionEndToEndId,
            debtorSortCode = malaiSortCode,
            debtorAccountNumber = fromAccountNumber(),
            debtorName = "Alice Walker",
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = 4_200L,
            description = "Energy DD",
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
                "rail" To "BACS_DIRECT_DEBIT"
                "direction" To "INBOUND"
                "createdAt" To "2026-05-25T09:00:00Z"
                "endToEndId" To collectionEndToEndId.toString()
                "fromAccountId" To someAccountUid().toString()
                "fromSortCode" To malaiSortCode
                "fromAccountNumber" To fromAccountNumber()
                "fromName" To "Alice Walker"
                "toSortCode" To creditorSortCode
                "toAccountNumber" To creditorAccountNumber
                "toName" To creditorName
                "amountMinorUnits" To 4_200
                "currency" To "GBP"
                "description" To "Energy DD"
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.SETTLED
            rail shouldBe Rail.BACS_DIRECT_DEBIT
            direction shouldBe PaymentDirection.INBOUND
            debtorAccountId shouldBe someAccountUid()
            debtorSortCode shouldBe malaiSortCode
            debtorAccountNo shouldBe fromAccountNumber()
            debtorName shouldBe "Alice Walker"
            beneficiaryAccountId shouldBe null
            beneficiarySortCode shouldBe creditorSortCode
            beneficiaryAccountNo shouldBe creditorAccountNumber
            beneficiaryName shouldBe creditorName
            returnReason shouldBe null
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/transactions"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To something
                "type" To "DIRECT_DEBIT_COLLECTION"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-05-25T09:00:00Z"
                "description" To "Energy DD"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -4_200
                        "counterpartyId" To BACS_INBOUND_SUSPENSE_ACCOUNT_ID.toString()
                        "description" To "Energy DD"
                        noExtraProps()
                    },
                    json {
                        "accountId" To BACS_INBOUND_SUSPENSE_ACCOUNT_ID.toString()
                        "amountMinorUnits" To 4_200
                        "counterpartyId" To someAccountUid().toString()
                        "description" To "Energy DD"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "INBOUND_COLLECTION_SETTLED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["resource_type"] shouldBe "Payment"
            this[0]["resource_id"] shouldBe somePaymentUid().toString()
        }

        and(thePublishedCollectionReturns()) {
            this.shouldBeEmpty()
        }
    }

    @Test
    fun duplicateCollectionNotifyIsDeduplicatedAndDebitsOnlyOnce() = malaiAcceptanceTest {
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
        and(postTransactionReturns(aPostTransactionResponse()
            .withTransactionId(someTransferUid())
            .withPostedAt("2026-05-25T09:00:00Z")))

        val collectionEndToEndId = UUID.fromString("00000000-0000-4000-9000-000000009100")

        and(theStubPublishesBacsCollection(
            endToEndId = collectionEndToEndId,
            debtorSortCode = malaiSortCode,
            debtorAccountNumber = fromAccountNumber(),
            debtorName = "Alice Walker",
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = 4_200L,
        ))
        and(theStubPublishesBacsCollection(
            endToEndId = collectionEndToEndId,
            debtorSortCode = malaiSortCode,
            debtorAccountNumber = fromAccountNumber(),
            debtorName = "Alice Walker",
            creditorSortCode = creditorSortCode,
            creditorAccountNumber = creditorAccountNumber,
            creditorName = creditorName,
            amountMinorUnits = 4_200L,
        ))

        and(thePaymentsInTheDatabase()) {
            this shouldHaveSize 1
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.count { it["action"] == "INBOUND_COLLECTION_SETTLED" } shouldBe 1
        }
    }
}
