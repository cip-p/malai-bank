package com.malai.test.e2e.tests

import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.USER_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.*

/**
 * E2E coverage for the Plan 02-14 payee endpoints against a running payee-service container.
 * One end-to-end ride: register a user → create a payee → list payees → delete the payee →
 * list payees and confirm the holder is back to empty.
 */
class PayeeTest : MalaiE2ETest() {

    @Test
    fun payee() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .thatIsRegistered()
        )

        // create payee
        whenever(payeeServiceReceivesCreatePayee(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            idempotencyKey = "e2e-payee-create-key",
            name = "Mum",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            nickname = "Mum's birthday account",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "payeeId" To something
                "name" To "Mum"
                "sortCode" To "12-34-56"
                "accountNumber" To "11111111"
                "nickname" To "Mum's birthday account"
                "createdAt" To something
                noExtraProps()
            }
        }

        val payeeId = uuidFromLastResponse("payeeId")

        // make the same request, it should work when using the same idempotencyKey
        whenever(payeeServiceReceivesCreatePayee(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            idempotencyKey = "e2e-payee-create-key",
            name = "Mum",
            sortCode = "12-34-56",
            accountNumber = "11111111",
            nickname = "Mum's birthday account",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "payeeId" To payeeId.toString()
            }
        }

        // list payees
        whenever(payeeServiceReceivesGetPayees(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payees" To listOf(
                    json {
                        "payeeId" To payeeId.toString()
                        "name" To "Mum"
                        "sortCode" To "12-34-56"
                        "accountNumber" To "11111111"
                        "nickname" To "Mum's birthday account"
                        "createdAt" To something
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        // delete payee
        whenever(payeeServiceReceivesDeletePayee(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
            payeeId = payeeId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        // list payees again — empty
        whenever(payeeServiceReceivesGetPayees(
            userId = capturedUuid(USER_ID),
            accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "payees" To emptyList<Any>()
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
                AuditEventInDb(8, someUserId(), "CUSTOMER", "PAYEE_ADDED"),
                AuditEventInDb(9, someUserId(), "CUSTOMER", "PAYEE_REMOVED"),
            )
        }
    }
}
