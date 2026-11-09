package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.USER_ID
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

class AccountHolderTest : MalaiE2ETest() {

    @Test
    fun accountHolder() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .thatIsRegistered()
        )

        // create a *second* account holder - one was created during onboardding
        whenever(accountHolderServiceReceivesCreateAccountHolder(
            userId = capturedUuid(USER_ID),
            accountHolderType = "INDIVIDUAL",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderUid" To something
                "accountHolderType" To "INDIVIDUAL"
                noExtraProps()
            }
        }

        capture(ACCOUNT_HOLDER_ID, uuidFromLastResponse("accountHolderUid"))

        // create individual
        whenever(accountHolderServiceReceivesCreateIndividual(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            title = "Mr",
            firstName = "John",
            lastName = "Doe",
            dateOfBirth = "1990-01-15",
            email = "john.doe@example.com",
            phone = "+447123456789",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "firstName" To "John"
            }
        }

        // get account holder
        whenever(accountHolderServiceReceivesGetAccountHolder(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderUid" To capturedUuid(ACCOUNT_HOLDER_ID).toString()
                "accountHolderType" To "INDIVIDUAL"
                noExtraProps()
            }
        }

        // get individual
        whenever(accountHolderServiceReceivesGetIndividual(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "email" To "john.doe@example.com"
            }
        }

        // get account holder name
        whenever(accountHolderServiceReceivesGetAccountHolderName(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolderName" To "John Doe"
                noExtraProps()
            }
        }

        // get all account holders — both the orchestrator-created Alice Smith (from
        // `thatIsRegistered()`) and the manually-created John Doe should be returned.
        // Order is insertion-order via Postgres, so Alice comes first.
        whenever(accountHolderServiceReceivesGetAllAccountHolders(
            userId = capturedUuid(USER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountHolders" To listOf(
                    json {
                        "accountHolderUid" To something
                        "accountHolderName" To "Alice Smith"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    },
                    json {
                        "accountHolderUid" To capturedUuid(ACCOUNT_HOLDER_ID).toString()
                        "accountHolderName" To "John Doe"
                        "accountHolderType" To "INDIVIDUAL"
                        noExtraProps()
                    }
                )
                noExtraProps()
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
            )
        }
    }

    @Test
    fun getAccountHolderRejectsRequestWithoutJwt() = malaiTest {
        whenever(accountHolderServiceReceives(
            get()
                .url("/api/v1/account-holder/all")
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
