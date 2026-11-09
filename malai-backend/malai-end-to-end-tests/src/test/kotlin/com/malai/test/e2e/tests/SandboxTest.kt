package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.CounterpartyDirectory
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Fps
import com.malai.test.acceptance.kensa.MalaiParty.Companion.SandboxService
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.WithFunding.Companion.FPS_INBOUND_SUSPENSE_ACCOUNT_ID
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.ACCEPTED
import org.springframework.http.HttpStatus.OK

class SandboxTest : MalaiE2ETest() {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun getCounterpartiesForwardsToTheStubAndReturnsTheCatalogue() = malaiTest {
        given(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        whenever(sandboxServiceReceivesGetCounterparties(userId = someUserId()))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "counterparties" To something
            }
        }

        and(theFollowingHttpRequestWasMade(SandboxService, CounterpartyDirectory) {
            method == GET && url.endsWith("/scenarios/counterparties")
        }) {
            method shouldBe GET
        }
    }

    @Test
    fun inboundFpsPaymentForwardsToTheStubAndCreditsTheBeneficiaryAccount() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(theLedgerSystemAccountsAreRegistered())

        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val beneficiaryAccountId = capturedUuid(ACCOUNT_ID)
        val beneficiaryAccountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)

        whenever(sandboxServiceReceivesInboundPayment(
            userId = someUserId(),
            accountHolderId = beneficiaryAccountHolderId,
            idempotencyKey = "e2e-sandbox-fps-inbound-1",
            rail = "FPS",
            creditedAccountId = beneficiaryAccountId,
            originatorSortCode = "60-00-01",
            originatorAccountNumber = "12345678",
            originatorName = "Bob Marley",
            amountMinorUnits = 5_000L,
            reference = "salary",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe ACCEPTED
        }

        and(theFollowingHttpRequestWasMade(SandboxService, Fps) {
            method == POST && url.endsWith("/scenarios/inbound/fps")
        }) {
            method shouldBe POST
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
}
