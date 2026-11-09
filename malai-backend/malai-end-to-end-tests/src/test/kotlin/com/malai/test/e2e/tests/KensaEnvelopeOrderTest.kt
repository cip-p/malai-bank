package com.malai.test.e2e.tests

import com.malai.commons.tracking.TestIdContext
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.MalaiE2ETestContext
import com.malai.test.e2e.WithCognito
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.kensa.KensaEventConsumer
import io.kotest.matchers.collections.shouldContainInOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verifies that the new SQS-backed kensa mechanism delivers envelopes to `kensa-events.fifo`
 * in correct chronological order across HTTP and SQS captures. The test scenario is a clone of
 * [PaymentsTest.fpsToCrossBankReturningDestinationLandsPendingThenReturns] — chosen because it
 * touches every transport (test → service HTTP, inter-service HTTP, SQS publish, SQS consume)
 * and involves both bank-side controllers and gateway-side listeners. Business assertions are
 * dropped — the only invariant under test here is the order of envelopes at the queue.
 *
 * Uses `shouldContainInOrder` so the listed sequence must be a subsequence of the actual
 * arrival order (other envelopes may appear between consecutive list items). Setup envelopes
 * (user registration, Cognito token fetches for the seeding helpers, …) precede the asserted
 * sequence and aren't enumerated.
 */
class KensaEnvelopeOrderTest : MalaiE2ETest(), WithCognito {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun fpsOutboundReturnEnvelopesArriveInChronologicalOrder() {
        malaiTest {
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

            and(thePaymentReachesStateInTheDatabase(
                uuidFromLastResponse("paymentId"),
                "RETURNED",
            ))
        }

        val summaries = MalaiE2ETestContext.kensaEventConsumer
            .receivedEnvelopesFor(TestIdContext.value.value)
            .map(KensaEventConsumer.Companion::summarise)

        summaries shouldContainInOrder listOf(
            "Initiate Payment Request",
            "Get Service Token Request",
            "Get Service Token Response",
            "Get Internal Account Request",
            "Get Internal Account Response",
            "Resolve Internal Account By Identifier Request",
            "Resolve Internal Account By Identifier Response",
            "Place Ledger Hold Request",
            "Place Ledger Hold Response",
            "Fps Command Submit",
            "Initiate Payment Response",
        )

        summaries shouldContainInOrder listOf(
            "Fps Command Submit",
            "Submit FPS Payment Request",
            "FPS Return Callback Request",
            "Fps Event Returned",
            "FPS Return Callback Response",
            "Submit FPS Payment Response",
        )

        summaries shouldContainInOrder listOf(
            "Fps Event Returned",
            "Release Ledger Hold Request",
            "Release Ledger Hold Response",
        )
    }
}
