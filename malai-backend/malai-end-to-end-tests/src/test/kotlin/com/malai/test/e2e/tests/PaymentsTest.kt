package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.WithCognito
import com.malai.test.e2e.WithFunding.Companion.BACS_INBOUND_SUSPENSE_ACCOUNT_ID
import com.malai.test.e2e.WithFunding.Companion.FPS_INBOUND_SUSPENSE_ACCOUNT_ID
import com.malai.test.e2e.WithFunding.Companion.SCHEME_SETTLEMENT_ACCOUNT_ID
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.fixtures.createPreconditionAccount
import com.malai.test.e2e.fixtures.readAccountSortCodeAndNumber
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.UNAUTHORIZED

class PaymentsTest : MalaiE2ETest(), WithCognito {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun initiatePaymentRejectsRequestWithoutJwt() = malaiTest {
        whenever(paymentsServiceReceives(
            post()
                .url("/api/v1/payments")
                .body("""{"fromAccountId":"00000000-0000-4000-8000-000000000001","toSortCode":"65-82-37","toAccountNumber":"10000010","amountMinorUnits":100,"currency":"GBP"}""")
                .contentType("application/json")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }

    @Test
    fun bookTransferBetweenOwnAccountsSettlesSynchronously() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val primaryAccountId = capturedUuid(ACCOUNT_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        val savingsAccountId = createPreconditionAccount(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            accountType = "SAVINGS",
            name = "Savings",
        )
        val (savingsSortCode, savingsAccountNumber) = readAccountSortCodeAndNumber(
            accountId = savingsAccountId,
            userId = someUserId(),
            accountHolderId = accountHolderId,
        )

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = primaryAccountId,
            amountMinorUnits = 10_000L,
        ))

        whenever(paymentsServiceReceivesInitiatePayment(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-book-1",
            fromAccountId = primaryAccountId,
            toSortCode = savingsSortCode,
            toAccountNumber = savingsAccountNumber,
            amountMinorUnits = 2_500L,
            description = "savings top-up",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "SETTLED"
                "rail" To "BOOK_TRANSFER"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To primaryAccountId.toString()
                "toAccountId" To savingsAccountId.toString()
                "toSortCode" To savingsSortCode
                "toAccountNumber" To savingsAccountNumber
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
                "description" To "savings top-up"
                noExtraProps()
            }
        }

        and(theLedgerEntriesForAccountInTheDatabase(primaryAccountId)) {
            this shouldHaveSize 2
            this[0].amountMinorUnits shouldBe 10_000L
            this[1].amountMinorUnits shouldBe -2_500L
        }

        and(theLedgerEntriesForAccountInTheDatabase(savingsAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 2_500L
        }
    }

    @Test
    fun fpsToCrossBankReturningDestinationLandsPendingThenReturns() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val sendersAccountId = capturedUuid(ACCOUNT_ID)
        val sendersAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = sendersAccountId,
            amountMinorUnits = 10_000L,
        ))

        whenever(paymentsServiceReceivesInitiatePayment(
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
            idempotencyKey = "e2e-fps-1",
            fromAccountId = sendersAccountId,
            toSortCode = "60-00-01",
            toAccountNumber = "99000001",
            amountMinorUnits = 5_000L,
            description = "to closed account",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toSortCode" To "60-00-01"
                "toAccountNumber" To "99000001"
                "amountMinorUnits" To 5_000
                "currency" To "GBP"
                "description" To "to closed account"
                noExtraProps()
            }
        }

        val paymentId = uuidFromLastResponse("paymentId")

        and(thePaymentReachesStateInTheDatabase(paymentId, "RETURNED"))

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 10_000L
        }
    }

    @Test
    fun fpsToCrossBankDestinationLandsPendingThenSettles() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val sendersAccountId = capturedUuid(ACCOUNT_ID)
        val sendersAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = sendersAccountId,
            amountMinorUnits = 20_000L,
        ))

        whenever(paymentsServiceReceivesInitiatePayment(
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
            idempotencyKey = "e2e-fps-xbank-1",
            fromAccountId = sendersAccountId,
            toSortCode = "60-00-01",
            toAccountNumber = "12345678",
            amountMinorUnits = 7_500L,
            description = "to NatWest payee",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "PENDING"
                "rail" To "FPS"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toSortCode" To "60-00-01"
                "toAccountNumber" To "12345678"
                "amountMinorUnits" To 7_500
                "currency" To "GBP"
                "description" To "to NatWest payee"
                noExtraProps()
            }
        }

        val paymentId = uuidFromLastResponse("paymentId")

        and(thePaymentReachesStateInTheDatabase(paymentId, "SETTLED"))

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 2
            this[0].amountMinorUnits shouldBe 20_000L
            this[1].amountMinorUnits shouldBe -7_500L
        }

        and(theLedgerEntriesForAccountInTheDatabase(SCHEME_SETTLEMENT_ACCOUNT_ID)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 7_500L
        }
    }

    @Test
    fun swiftToEuroDestinationConvertsCurrencyLandsPendingThenSettles() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val sendersAccountId = capturedUuid(ACCOUNT_ID)
        val sendersAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        and(theFollowingBalanceIsSeededOnAccount(
            accountId = sendersAccountId,
            amountMinorUnits = 20_000L,
        ))

        whenever(paymentsServiceReceivesInitiateSwiftPayment(
            userId = someUserId(),
            accountHolderId = sendersAccountHolderId,
            idempotencyKey = "e2e-swift-eur-1",
            fromAccountId = sendersAccountId,
            toIban = "DE89370400440532013000",
            toBic = "COBADEFFXXX",
            toName = "Helga Mueller",
            paymentPurpose = "Rent",
            amountMinorUnits = 10_000L,
            currency = "EUR",
            description = "rent in Berlin",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "paymentId" To something
                "state" To "PENDING"
                "rail" To "SWIFT"
                "direction" To "OUTBOUND"
                "createdAt" To something
                "endToEndId" To something
                "fromAccountId" To sendersAccountId.toString()
                "toIban" To "DE89370400440532013000"
                "toBic" To "COBADEFFXXX"
                "toName" To "Helga Mueller"
                "paymentPurpose" To "Rent"
                "amountMinorUnits" To 10_000
                "currency" To "GBP"
                "description" To "rent in Berlin"
                "fxConversion" To json {
                    "amountMinorUnits" To 11_500
                    "currency" To "EUR"
                    "rateUsed" To "1.15"
                    noExtraProps()
                }
                noExtraProps()
            }
        }

        val paymentId = uuidFromLastResponse("paymentId")

        and(thePaymentReachesStateInTheDatabase(paymentId, "SETTLED"))

        and(theLedgerEntriesForAccountInTheDatabase(sendersAccountId)) {
            this shouldHaveSize 2
            this[0].amountMinorUnits shouldBe 20_000L
            this[1].amountMinorUnits shouldBe -10_000L
        }

        and(theLedgerEntriesForAccountInTheDatabase(SCHEME_SETTLEMENT_ACCOUNT_ID)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 10_000L
        }
    }

    @Test
    fun fpsInboundCreditsKnownBeneficiaryAccount() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val beneficiaryAccountId = capturedUuid(ACCOUNT_ID)
        val beneficiaryAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val (beneficiarySortCode, beneficiaryAccountNumber) = readAccountSortCodeAndNumber(
            accountId = beneficiaryAccountId,
            userId = someUserId(),
            accountHolderId = beneficiaryAccountHolderId,
        )

        whenever(malaiStubReceivesFpsInbound(
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = "Alice Walker",
            originatorSortCode = "60-00-01",
            originatorAccountNumber = "12345678",
            originatorName = "Bob Marley",
            amountMinorUnits = 5_000L,
            description = "salary",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(aPaymentForBeneficiaryReachesStateInTheDatabase(
            beneficiaryAccountId = beneficiaryAccountId,
            direction = "INBOUND",
            expectedState = "SETTLED",
        ))

        and(theLedgerEntriesForAccountInTheDatabase(beneficiaryAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 5_000L
        }

        and(theLedgerEntriesForAccountInTheDatabase(FPS_INBOUND_SUSPENSE_ACCOUNT_ID)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe -5_000L
        }
    }

    @Test
    fun fpsInboundToUnknownBeneficiaryProducesReturnToSender() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())
        and(cognitoCanIssueServiceToken())

        val unknownBeneficiaryAccountNumber = (10_000_000..99_999_999).random().toString()

        whenever(malaiStubReceivesFpsInbound(
            beneficiarySortCode = "65-82-37",
            beneficiaryAccountNumber = unknownBeneficiaryAccountNumber,
            beneficiaryName = "Nobody Here",
            originatorSortCode = "60-00-01",
            originatorAccountNumber = "12345678",
            originatorName = "Bob Marley",
            amountMinorUnits = 5_000L,
            description = "misdirected salary",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(theMalaiStubReceivedAnFpsReturnToSenderForBeneficiary(unknownBeneficiaryAccountNumber))
    }

    @Test
    fun bacsInboundCreditsKnownBeneficiaryAccount() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val beneficiaryAccountId = capturedUuid(ACCOUNT_ID)
        val beneficiaryAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val (beneficiarySortCode, beneficiaryAccountNumber) = readAccountSortCodeAndNumber(
            accountId = beneficiaryAccountId,
            userId = someUserId(),
            accountHolderId = beneficiaryAccountHolderId,
        )

        whenever(malaiStubReceivesBacsInbound(
            beneficiarySortCode = beneficiarySortCode,
            beneficiaryAccountNumber = beneficiaryAccountNumber,
            beneficiaryName = "Alice Walker",
            originatorSortCode = "20-00-05",
            originatorAccountNumber = "20000020",
            originatorName = "ACME Employer Ltd",
            amountMinorUnits = 250_000L,
            description = "Salary",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        and(aPaymentForBeneficiaryReachesStateInTheDatabase(
            beneficiaryAccountId = beneficiaryAccountId,
            direction = "INBOUND",
            expectedState = "SETTLED",
        ))

        and(theLedgerEntriesForAccountInTheDatabase(beneficiaryAccountId)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe 250_000L
        }

        and(theLedgerEntriesForAccountInTheDatabase(BACS_INBOUND_SUSPENSE_ACCOUNT_ID)) {
            this shouldHaveSize 1
            this[0].amountMinorUnits shouldBe -250_000L
        }
    }
}
