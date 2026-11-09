package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.*
import com.malai.test.e2e.fixtures.FixtureCapture.capture
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED

class AccountsTest : MalaiE2ETest() {

    @Test
    fun accounts() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .thatIsRegistered()
        )

        // create account
        whenever(accountServiceReceivesCreateAccount(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            accountType = "PRIMARY",
            currency = "GBP",
            name = "Personal",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To something
                "accountType" To "PRIMARY"
            }
        }

        capture(ACCOUNT_ID, uuidFromLastResponse("accountUid"))

        // D-2.5: a brand-new account has no ledger_entries, so balance projects to 0.
        // Funding via TransfersController (Plan 02-12) and the seeder (Plan 02-15) is
        // the only way to put money on a ledger; e2e here asserts the zero-projection.
        whenever(accountServiceReceivesGetBalance(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            accountUid = capturedUuid(ACCOUNT_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "currency" To "GBP"
                "amountMinorUnits" To 0
                "pendingMinorUnits" To null
            }
        }

        // get accounts
        whenever(accountServiceReceivesGetAccounts(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accounts" To listOf(
                    json {
                        "accountUid" To capturedUuid(ACCOUNT_ID).toString()
                        "accountType" To "PRIMARY"
                    }
                )
            }
        }

        and(theAuditEventsInTheDatabase()) {
            mapAuditEvents() shouldBe listOf(
                AuditEventInDb(1, someUserId(), "SYSTEM", "ACCOUNT_HOLDER_ONBOARDED"),
                AuditEventInDb(2, null, "SYSTEM", "INDIVIDUAL_ONBOARDED"),
                AuditEventInDb(3, null, "SYSTEM", "ADDRESS_ONBOARDED"),
                AuditEventInDb(4, someUserId(), "SYSTEM", "ACCOUNT_ONBOARDED"),
                AuditEventInDb(5, someUserId(), "CUSTOMER", "REGISTERED"),
                AuditEventInDb(6, someUserId(), "CUSTOMER", "ACCOUNT_HOLDER_CREATED"),
                AuditEventInDb(7, null, "CUSTOMER", "INDIVIDUAL_CREATED"),
                AuditEventInDb(8, someUserId(), "CUSTOMER", "ACCOUNT_CREATED"),
            )
        }
    }

    @Test
    fun getAccountsRejectsRequestWithoutJwt() = malaiTest {
        whenever(accountServiceReceives(
            get()
                .url("/api/v1/accounts")
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe UNAUTHORIZED
            body containsProp json {
                "message" To "5982"
                noExtraProps()
            }
        }
    }
}
