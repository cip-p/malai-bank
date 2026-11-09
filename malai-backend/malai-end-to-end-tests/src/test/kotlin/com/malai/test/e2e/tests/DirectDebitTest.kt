package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.DirectDebitService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.SandboxService
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.CaptureType.USER_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.HttpStatus.OK
import org.springframework.http.HttpStatus.UNAUTHORIZED
import java.time.LocalDate
import java.time.ZoneOffset.UTC

/**
 * E2E coverage for the direct-debit lifecycle against a running direct-debit-service container.
 * Direct debits are creditor-originated — there is no customer-facing create surface — so the
 * mandate is lodged bank-side through sandbox-service, which snapshots a fictional creditor from
 * the counterparty directory and writes the mandate through direct-debit-service's internal
 * endpoint. One end-to-end ride: register a holder with an account → lodge a mandate → list it
 * (ACTIVE) → cancel it → list it again (CANCELLED, cancellation timestamp stamped).
 *
 * `startDate` is computed a week ahead of the container's real clock (e2e produces real timestamps)
 * so it always clears the start-date-in-the-past guard; `nextCollectionAt` is born at the start of
 * that day in UTC. Octopus Energy Ltd (60-00-01 / 22334455) is a `SETTLES` row in the stub's
 * fictional directory.
 */
class DirectDebitTest : MalaiE2ETest() {

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun directDebit() = malaiTest {
        val startDate = LocalDate.now(UTC).plusDays(7)

        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        whenever(sandboxServiceReceivesLodgeDirectDebit(
            userId = userId,
            accountHolderId = accountHolderId,
            accountId = accountId,
            creditorSortCode = "60-00-01",
            creditorAccountNumber = "22334455",
            amountMinorUnits = 4_500L,
            frequency = "MONTHLY",
            startDate = startDate.toString(),
            reference = "DD-OCTOPUS",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "directDebitId" To something
                "accountId" To accountId.toString()
                "creditorName" To "Octopus Energy Ltd"
                "creditorSortCode" To "60-00-01"
                "creditorAccountNumber" To "22334455"
                "reference" To "DD-OCTOPUS"
                "amountMinorUnits" To 4_500
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To startDate.toString()
                "nextCollectionAt" To "${startDate}T00:00:00Z"
                noExtraProps()
            }
        }

        val directDebitId = uuidFromLastResponse("directDebitId")

        and(theFollowingHttpRequestWasMade(SandboxService, DirectDebitService) {
            method == POST && url.endsWith("/api/v1/internal/direct-debits")
        }) {
            method shouldBe POST
        }

        whenever(directDebitServiceReceivesGetDirectDebits(
            userId = userId,
            accountHolderId = accountHolderId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To listOf(
                    json {
                        "directDebitId" To directDebitId.toString()
                        "accountId" To accountId.toString()
                        "creditorName" To "Octopus Energy Ltd"
                        "creditorSortCode" To "60-00-01"
                        "creditorAccountNumber" To "22334455"
                        "reference" To "DD-OCTOPUS"
                        "amountMinorUnits" To 4_500
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To startDate.toString()
                        "nextCollectionAt" To "${startDate}T00:00:00Z"
                        noExtraProps()
                    }
                )
                noExtraProps()
            }
        }

        whenever(directDebitServiceReceivesCancelDirectDebit(
            userId = userId,
            accountHolderId = accountHolderId,
            directDebitId = directDebitId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe NO_CONTENT
        }

        whenever(directDebitServiceReceivesGetDirectDebits(
            userId = userId,
            accountHolderId = accountHolderId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To listOf(
                    json {
                        "directDebitId" To directDebitId.toString()
                        "accountId" To accountId.toString()
                        "creditorName" To "Octopus Energy Ltd"
                        "creditorSortCode" To "60-00-01"
                        "creditorAccountNumber" To "22334455"
                        "reference" To "DD-OCTOPUS"
                        "amountMinorUnits" To 4_500
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "CANCELLED"
                        "startDate" To startDate.toString()
                        "cancelledAt" To something
                        "nextCollectionAt" To "${startDate}T00:00:00Z"
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
                AuditEventInDb(8, someUserId(), "CUSTOMER", "ACCOUNT_CREATED"),
                AuditEventInDb(9, null, "SYSTEM", "DIRECT_DEBIT_MANDATE_LODGED"),
                AuditEventInDb(10, someUserId(), "CUSTOMER", "DIRECT_DEBIT_CANCELLED"),
            )
        }
    }

    @Test
    fun getDirectDebitsRejectsRequestWithoutJwt() = malaiTest {
        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/direct-debits")
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
