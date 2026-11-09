package com.malai.test.e2e.tests

import com.malai.test.acceptance.kensa.MalaiParty.Companion.AccountService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.DirectDebitService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.InsightsService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.LedgerService
import com.malai.test.acceptance.kensa.MalaiParty.Companion.StandingOrderService
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.AuditEventInDb
import com.malai.test.e2e.MalaiE2ETest
import com.malai.test.e2e.SeededSpend
import com.malai.test.e2e.WithCognito
import com.malai.test.e2e.fixtures.AccountBuilderFixture.Companion.anAccount
import com.malai.test.e2e.fixtures.AccountHolderBuilderFixture.Companion.anIndividualAccountHolder
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_HOLDER_ID
import com.malai.test.e2e.fixtures.CaptureType.ACCOUNT_ID
import com.malai.test.e2e.fixtures.CaptureType.USER_ID
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import com.malai.test.e2e.fixtures.UserBuilderFixture.Companion.anUser
import com.malai.test.e2e.fixtures.createPreconditionPayee
import com.malai.test.e2e.mapAuditEvents
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Something.something
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.DELETE
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import org.springframework.http.HttpMethod.PUT
import org.springframework.http.HttpStatus.OK
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.util.UUID

/**
 * E2E coverage for the spending-insights feature. The customer surface lives on account-service
 * (`/api/v1/accounts/{id}/spending`, `/transactions`, `.../category`); each read/write fans out to
 * the internal insights-service, which categorises the ledger's entries, aggregates the monthly
 * donut, and stores customer recategorisation overrides. These tests drive the customer surface and
 * assert both the response and the account-service → insights-service arrow on the Kensa diagram.
 *
 * A fixed month (`2026-05`) of categorisable spend is seeded straight onto the ledger so the run is
 * independent of the container's real clock. Categorisation runs against the real `Categoriser`, so
 * the seeded narratives lean on its keyword rules: "Tesco" → GROCERIES, "Octopus Energy" → BILLS,
 * "rent" → HOUSING, and a positive amount → INCOME (which the donut excludes as non-spending).
 */
class InsightsTest : MalaiE2ETest(), WithCognito {

    private val month = "2026-05"

    private val groceriesEntryOne = UUID.fromString("00000000-0000-4000-9000-000000000001")
    private val groceriesEntryTwo = UUID.fromString("00000000-0000-4000-9000-000000000002")
    private val billsEntry = UUID.fromString("00000000-0000-4000-9000-000000000003")
    private val housingEntry = UUID.fromString("00000000-0000-4000-9000-000000000004")
    private val incomeEntry = UUID.fromString("00000000-0000-4000-9000-000000000005")

    @BeforeEach
    fun resetDatabaseBetweenTests() {
        cleanTables()
    }

    @Test
    fun spendingBreakdownGroupsTheMonthsSpendByCategory() = malaiTest {
        given(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingSpendIsSeededOnAccount(accountId, month, aMonthOfSpend()))

        whenever(accountServiceReceivesGetSpending(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
            month = month,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To month
                "currency" To "GBP"
                "categories" To listOf(
                    json { "category" To "HOUSING"; "totalMinorUnits" To 9_500; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "BILLS"; "totalMinorUnits" To 7_800; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 4_000; "transactionCount" To 2; noExtraProps() },
                )
                "totalMinorUnits" To 21_300
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == POST && url.endsWith("/api/v1/internal/insights/spending-breakdown")
        }) {
            method shouldBe POST
        }
    }

    @Test
    fun transactionFeedIsCategorisedByInsights() = malaiTest {
        given(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingSpendIsSeededOnAccount(accountId, month, aMonthOfSpend()))

        whenever(accountServiceReceivesGetTransactions(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To accountId.toString()
                "transactions" To listOf(
                    feedItem(incomeEntry, 250_000, "ACME Salary", "INCOME"),
                    feedItem(housingEntry, -9_500, "Monthly rent", "HOUSING"),
                    feedItem(billsEntry, -7_800, "Octopus Energy bill", "BILLS"),
                    feedItem(groceriesEntryTwo, -1_500, "Tesco lunch", "GROCERIES"),
                    feedItem(groceriesEntryOne, -2_500, "Tesco weekly shop", "GROCERIES"),
                )
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == POST && url.endsWith("/api/v1/internal/insights/categorisations")
        }) {
            method shouldBe POST
        }
    }

    @Test
    fun recategorisingATransactionUpdatesTheLiveBreakdownAndIsReversible() = malaiTest {
        given(theAuditChainIsAtGenesis())
        and(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingSpendIsSeededOnAccount(accountId, month, aMonthOfSpend()))

        whenever(accountServiceReceivesOverrideCategory(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
            entryId = groceriesEntryOne,
            category = "EATING_OUT",
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == PUT && url.endsWith("/api/v1/internal/insights/overrides/$groceriesEntryOne")
        }) {
            method shouldBe PUT
        }

        whenever(accountServiceReceivesGetSpending(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
            month = month,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To month
                "currency" To "GBP"
                "categories" To listOf(
                    json { "category" To "HOUSING"; "totalMinorUnits" To 9_500; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "BILLS"; "totalMinorUnits" To 7_800; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "EATING_OUT"; "totalMinorUnits" To 2_500; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 1_500; "transactionCount" To 1; noExtraProps() },
                )
                "totalMinorUnits" To 21_300
                noExtraProps()
            }
        }

        whenever(accountServiceReceivesClearCategoryOverride(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
            entryId = groceriesEntryOne,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == DELETE && url.endsWith("/api/v1/internal/insights/overrides/$groceriesEntryOne")
        }) {
            method shouldBe DELETE
        }

        whenever(accountServiceReceivesGetSpending(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
            month = month,
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "month" To month
                "currency" To "GBP"
                "categories" To listOf(
                    json { "category" To "HOUSING"; "totalMinorUnits" To 9_500; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "BILLS"; "totalMinorUnits" To 7_800; "transactionCount" To 1; noExtraProps() },
                    json { "category" To "GROCERIES"; "totalMinorUnits" To 4_000; "transactionCount" To 2; noExtraProps() },
                )
                "totalMinorUnits" To 21_300
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
                AuditEventInDb(9, someUserId(), "CUSTOMER", "CATEGORY_OVERRIDDEN"),
                AuditEventInDb(10, someUserId(), "CUSTOMER", "CATEGORY_OVERRIDE_CLEARED"),
            )
        }
    }

    @Test
    fun forecastProjectsActiveStandingOrdersAndDirectDebitsOntoTheCurrentMonth() = malaiTest {
        val today = LocalDate.now(UTC)

        given(anUser()
            .withUserId(someUserId())
            .withAccountHolder(anIndividualAccountHolder())
            .withAccount(anAccount().withAccountType("PRIMARY").withName("Personal"))
            .thatIsRegistered())

        val userId = capturedUuid(USER_ID)
        val accountHolderId = capturedUuid(ACCOUNT_HOLDER_ID)
        val accountId = capturedUuid(ACCOUNT_ID)

        and(theFollowingBalanceIsSeededOnAccount(accountId, 500_000L))

        val payeeId = createPreconditionPayee(
            userId = userId,
            accountHolderId = accountHolderId,
            name = "Landlord",
            sortCode = "20-00-00",
            accountNumber = "12345678",
            idempotencyKey = "e2e-forecast-payee",
        )

        whenever(standingOrderServiceReceivesCreateStandingOrder(
            userId = userId,
            accountHolderId = accountHolderId,
            idempotencyKey = "e2e-forecast-standing-order",
            accountId = accountId,
            payeeId = payeeId,
            amountMinorUnits = 95_000L,
            frequency = "MONTHLY",
            startDate = today.toString(),
            reference = "SO-RENT",
        ))

        val standingOrderId = uuidFromLastResponse("standingOrderId")

        whenever(sandboxServiceReceivesLodgeDirectDebit(
            userId = userId,
            accountHolderId = accountHolderId,
            accountId = accountId,
            creditorSortCode = "60-00-01",
            creditorAccountNumber = "22334455",
            amountMinorUnits = 4_500L,
            frequency = "MONTHLY",
            startDate = today.toString(),
            reference = "DD-OCTOPUS",
        ))

        val directDebitId = uuidFromLastResponse("directDebitId")

        whenever(accountServiceReceivesGetForecast(
            userId = userId,
            accountHolderId = accountHolderId,
            accountUid = accountId,
        ))

        val rentCompleted = json {
            "date" To something
            "counterpartyName" To "Landlord"
            "amountMinorUnits" To -95_000
            "type" To "STANDING_ORDER"
            "instrumentId" To standingOrderId.toString()
            noExtraProps()
        }
        val octopusCompleted = json {
            "date" To something
            "counterpartyName" To "Octopus Energy Ltd"
            "amountMinorUnits" To -4_500
            "type" To "DIRECT_DEBIT"
            "instrumentId" To directDebitId.toString()
            noExtraProps()
        }
        val completedInProjectionOrder =
            listOf(standingOrderId to rentCompleted, directDebitId to octopusCompleted)
                .sortedBy { it.first }
                .map { it.second }

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "accountUid" To accountId.toString()
                "month" To something
                "windowStart" To something
                "windowEnd" To something
                "asOfDate" To something
                "currency" To "GBP"
                "availableBalanceMinorUnits" To 500_000
                "completedTotalMinorUnits" To -99_500
                "expectedTotalMinorUnits" To 0
                "forecastBalanceMinorUnits" To 500_000
                "completed" To completedInProjectionOrder
                "expected" To emptyList<Any>()
                noExtraProps()
            }
        }

        and(theFollowingHttpRequestWasMade(AccountService, LedgerService) {
            method == GET && url.contains("/balances")
        }) {
            method shouldBe GET
        }

        and(theFollowingHttpRequestWasMade(AccountService, StandingOrderService) {
            method == GET && url.contains("/api/v1/internal/standing-orders")
        }) {
            method shouldBe GET
        }

        and(theFollowingHttpRequestWasMade(AccountService, DirectDebitService) {
            method == GET && url.contains("/api/v1/internal/direct-debits")
        }) {
            method shouldBe GET
        }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == POST && url.endsWith("/api/v1/internal/insights/balance-forecast")
        }) {
            method shouldBe POST
        }
    }

    @Test
    fun forecastForAFreshlyRegisteredUserStraddlesTodayAndDipsIntoTheRed() = malaiTest {
        given(cognitoCanRegisterUser(someEmail(), someUserId()))
        and(cognitoCanIssueServiceToken())

        whenever(authServiceReceivesRegisterPasswordless(
            firstName = "Alice",
            lastName = "Smith",
            email = someEmail(),
        ))

        then(theLastResponseHas()) { statusCode shouldBe OK }

        and(cognitoCanVerifyRegistration(someUserId()))

        whenever(authServiceReceivesVerifyCode(someEmail(), "some-confirmation-code"))

        then(theLastResponseHas()) { statusCode shouldBe OK }

        whenever(accountHolderServiceReceivesGetAllAccountHolders(someUserId()))
        val accountHolderId = uuidFromLastResponseList("accountHolders", 0, "accountHolderUid")

        whenever(accountServiceReceivesGetAccounts(someUserId(), accountHolderId))
        val accountId = uuidFromLastResponseList("accounts", 0, "accountUid")

        whenever(accountServiceReceivesGetForecast(
            userId = someUserId(),
            accountHolderId = accountHolderId,
            accountUid = accountId,
        ))

        then(theLastResponseHas()) { statusCode shouldBe OK }

        val forecast = testingObjectMapper.readTree(lastResponse().body!!)
        val completed = forecast["completed"]
        val expected = forecast["expected"]
        val availableBalance = forecast["availableBalanceMinorUnits"].asLong()
        val expectedTotal = forecast["expectedTotalMinorUnits"].asLong()
        val forecastBalance = forecast["forecastBalanceMinorUnits"].asLong()
        val occurrenceTypes =
            (completed.values().map { it["type"].asString() } + expected.values().map { it["type"].asString() }).toSet()

        (completed.size() >= 1) shouldBe true
        (expected.size() >= 1) shouldBe true
        forecastBalance shouldBe availableBalance + expectedTotal
        (forecastBalance < 0L) shouldBe true
        occurrenceTypes.containsAll(listOf("STANDING_ORDER", "DIRECT_DEBIT")) shouldBe true

        and(theFollowingHttpRequestWasMade(AccountService, LedgerService) {
            method == GET && url.contains("/balances")
        }) { method shouldBe GET }

        and(theFollowingHttpRequestWasMade(AccountService, StandingOrderService) {
            method == GET && url.contains("/api/v1/internal/standing-orders")
        }) { method shouldBe GET }

        and(theFollowingHttpRequestWasMade(AccountService, DirectDebitService) {
            method == GET && url.contains("/api/v1/internal/direct-debits")
        }) { method shouldBe GET }

        and(theFollowingHttpRequestWasMade(AccountService, InsightsService) {
            method == POST && url.endsWith("/api/v1/internal/insights/balance-forecast")
        }) { method shouldBe POST }
    }

    private fun aMonthOfSpend() = listOf(
        SeededSpend(groceriesEntryOne, -2_500, "Tesco weekly shop"),
        SeededSpend(groceriesEntryTwo, -1_500, "Tesco lunch"),
        SeededSpend(billsEntry, -7_800, "Octopus Energy bill"),
        SeededSpend(housingEntry, -9_500, "Monthly rent"),
        SeededSpend(incomeEntry, 250_000, "ACME Salary"),
    )

    private fun feedItem(entryId: UUID, amountMinorUnits: Int, description: String, category: String) =
        json {
            "entryId" To entryId.toString()
            "transactionId" To something
            "postedAt" To something
            "currency" To "GBP"
            "amountMinorUnits" To amountMinorUnits
            "runningBalanceMinorUnits" To something
            "description" To description
            "category" To category
            noExtraProps()
        }
}
