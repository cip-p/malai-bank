package com.malai.payments.acceptance.happy

import com.malai.client.malai.ledger.builders.response.HoldResponseBuilder.Companion.aHoldResponse
import com.malai.client.malai.ledger.builders.response.PostTransactionResponseBuilder.Companion.aPostTransactionResponse
import com.malai.client.malai.ledger.builders.response.SettleHoldResponseBuilder.Companion.aSettleHoldResponse
import com.malai.client.malai.mdr.builders.response.RecordModelDecisionResponseBuilder.Companion.aRecordModelDecisionResponse
import com.malai.client.malai.payments.builders.request.InitiatePaymentRequestBuilder.Companion.anInitiatePaymentRequest
import com.malai.payments.acceptance.PaymentsAcceptanceTest
import com.malai.payments.entity.PaymentState
import com.malai.payments.support.WithPayments.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import java.util.UUID

class SwiftOutboundPaymentsControllerTest : PaymentsAcceptanceTest() {

    @Test
    fun swiftToEurLandsPendingPlacesAHoldOnTheSourceAndIncludesFxConversion() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001aaa"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse()
            .withHoldId(somePaymentUid())
            .withAccountId(someAccountUid())
            .withAmountMinorUnits(10_000L)
            .withStatus("PLACED")))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "swift-eur-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("DE89370400440532013000")
                    .withToBic("COBADEFFXXX")
                    .withAmountMinorUnits(10_000L)
                    .withCurrency("EUR")
                    .withPaymentPurpose("Rent")
                    .withToName("Helga Mueller"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
                "rail" To "SWIFT"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000001aaa"
                "fromAccountId" To someAccountUid().toString()
                "toIban" To "DE89370400440532013000"
                "toBic" To "COBADEFFXXX"
                "toName" To "Helga Mueller"
                "paymentPurpose" To "Rent"
                "amountMinorUnits" To 10_000
                "currency" To "GBP"
                "fxConversion" To json {
                    "amountMinorUnits" To 11_500
                    "currency" To "EUR"
                    "rateUsed" To "1.15"
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.PENDING
            rail.name shouldBe "SWIFT"
            beneficiaryIban shouldBe "DE89370400440532013000"
            beneficiaryBic shouldBe "COBADEFFXXX"
            paymentPurpose shouldBe "Rent"
            beneficiarySortCode shouldBe null
            beneficiaryAccountNo shouldBe null
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            body containsProp json {
                "holdId" To somePaymentUid().toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 10_000
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }

        and(theSubmittedSwiftOutbounds()) {
            this shouldHaveSize 1
            this[0].paymentId shouldBe somePaymentUid()
            this[0].beneficiaryIban shouldBe "DE89370400440532013000"
            this[0].beneficiaryBic shouldBe "COBADEFFXXX"
            this[0].targetAmountMinorUnits shouldBe 11_500L
            this[0].targetCurrency shouldBe "EUR"
            this[0].amountMinorUnits shouldBe 10_000L
        }
    }

    @Test
    fun swiftSettlementConsumerSettlesTheHoldToSchemeSettlement() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001bbb"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(somePaymentUid())
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "swift-settle-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("US56103000001234567890")
                    .withToBic("CHASUS33XXX")
                    .withAmountMinorUnits(5_000L)
                    .withCurrency("USD")
                    .withPaymentPurpose("Services"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        and(theStubScheduleSettlesSwiftPayment(
            paymentId = somePaymentUid(),
            endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001bbb"),
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
                "rail" To "SWIFT"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000001bbb"
                "fromAccountId" To someAccountUid().toString()
                "toIban" To "US56103000001234567890"
                "toBic" To "CHASUS33XXX"
                "paymentPurpose" To "Services"
                "amountMinorUnits" To 5_000
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds/${somePaymentUid()}/settle"
        }) {
            method shouldBe POST
            body containsProp json {
                "transactionId" To something
                "type" To "PAYMENT_SETTLEMENT"
                "initiatedById" To someAccountHolderUid().toString()
                "postedAt" To "2026-10-15T10:30:00Z"
                "entries" To listOf(
                    json {
                        "accountId" To someAccountUid().toString()
                        "amountMinorUnits" To -5_000
                        "counterpartyId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        noExtraProps()
                    },
                    json {
                        "accountId" To SCHEME_SETTLEMENT_ACCOUNT_ID.toString()
                        "amountMinorUnits" To 5_000
                        "counterpartyId" To someAccountUid().toString()
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.map { it["action"] } shouldBe listOf("PAYMENT_INITIATED", "PAYMENT_SETTLED")
        }
    }

    @Test
    fun duplicateSwiftSettledMessageIsDeduplicated() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001ccc"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))
        and(settleHoldReturns(aSettleHoldResponse()
            .withHoldId(somePaymentUid())
            .withTransaction(aPostTransactionResponse()
                .withTransactionId(UUID.randomUUID())
                .withPostedAt("2026-10-15T10:30:00Z"))))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "swift-dedup-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("FR1420041010050500013M02606")
                    .withToBic("BNPAFRPPXXX")
                    .withAmountMinorUnits(2_000L)
                    .withCurrency("GBP"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        val endToEndId = UUID.fromString("00000000-0000-4000-9000-000000001ccc")
        and(theStubScheduleSettlesSwiftPayment(somePaymentUid(), endToEndId))
        and(theStubScheduleSettlesSwiftPayment(somePaymentUid(), endToEndId))

        and(thePaymentInTheDatabase(somePaymentUid())) {
            state shouldBe PaymentState.SETTLED
        }

        and(theAuditEventRowsInTheDatabase()) {
            this.count { it["action"] == "PAYMENT_SETTLED" } shouldBe 1
        }
    }

    @Test
    fun swiftSameCurrencyGbpPlacesAHoldAndOmitsFxConversion() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(thereIsNoIdempotencyKeyInTheDatabase())
        and(thereAreNoPaymentsInTheDatabase())
        and(thereIsNoSqsMessageDedupInTheDatabase())
        and(theSwiftSchemeIsReset())
        and(nowIs("2026-10-15T10:30:00Z"))
        and(theNextGeneratedUuidWillBe(
            somePaymentUid(),
            UUID.fromString("00000000-0000-4000-9000-000000001ddd"),
        ))
        and(ownAccountsExistInLookup())
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(recordModelDecisionReturns(aRecordModelDecisionResponse()))
        and(placeHoldReturns(aHoldResponse().withHoldId(somePaymentUid()).withAccountId(someAccountUid())))

        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .header("Idempotency-Key", "swift-gbp-1")
                .header("Content-Type", "application/json")
                .body(anInitiatePaymentRequest()
                    .withFromAccountId(someAccountUid())
                    .withToIban("GB29NWBK60161331926819")
                    .withToBic("NWBKGB2LXXX")
                    .withAmountMinorUnits(7_500L)
                    .withCurrency("GBP"))
                .withCustomerJwtTokenForUserIdAndAccountHolderId(someUserId(), someAccountHolderUid())
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To somePaymentUid().toString()
                "state" To "PENDING"
                "rail" To "SWIFT"
                "direction" To "OUTBOUND"
                "createdAt" To "2026-10-15T10:30:00Z"
                "endToEndId" To "00000000-0000-4000-9000-000000001ddd"
                "fromAccountId" To someAccountUid().toString()
                "toIban" To "GB29NWBK60161331926819"
                "toBic" To "NWBKGB2LXXX"
                "amountMinorUnits" To 7_500
                "currency" To "GBP"
                noExtraProps()
            }
        }

        and(paymentsServiceMadeTheFollowingHttpRequest {
            url == "/ledger-service/api/v1/internal/ledger/holds"
        }) {
            method shouldBe POST
            body containsProp json {
                "holdId" To somePaymentUid().toString()
                "accountId" To someAccountUid().toString()
                "amountMinorUnits" To 7_500
                "placedAt" To "2026-10-15T10:30:00Z"
                noExtraProps()
            }
        }
    }
}
