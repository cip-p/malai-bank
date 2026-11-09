package com.malai.test.e2e.tests

import com.malai.test.e2e.MalaiE2ETest
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

class TransfersTest : MalaiE2ETest() {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
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

        whenever(accountServiceReceivesTransfer(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-transfer-1",
            fromAccountId = primaryAccountId,
            toSortCode = savingsSortCode,
            toAccountNumber = savingsAccountNumber,
            amountMinorUnits = 2_500L,
            description = "savings top-up",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "transactionId" To something
                "postedAt" To something
                "fromAccountId" To primaryAccountId.toString()
                "toAccountId" To savingsAccountId.toString()
                "amountMinorUnits" To 2_500
                "currency" To "GBP"
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
    fun `transfer replays cached response on duplicate Idempotency-Key without double-booking the ledger`() = malaiTest {
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

        whenever(accountServiceReceivesTransfer(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-transfer-replay-1",
            fromAccountId = primaryAccountId,
            toSortCode = savingsSortCode,
            toAccountNumber = savingsAccountNumber,
            amountMinorUnits = 2_500L,
            description = "savings top-up",
        ))

        val firstResponseStatus = lastResponse().statusCode
        val firstResponseBody = lastResponse().body
        firstResponseStatus shouldBe CREATED

        whenever(accountServiceReceivesTransfer(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-transfer-replay-1",
            fromAccountId = primaryAccountId,
            toSortCode = savingsSortCode,
            toAccountNumber = savingsAccountNumber,
            amountMinorUnits = 2_500L,
            description = "savings top-up",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body shouldBe firstResponseBody
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
}
