package com.malai.directdebit.acceptance.happy

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.commons.schedule.ScheduledPaymentEndToEndId
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.scheduler.DirectDebitCollectionRequester
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod.POST
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class DirectDebitCollectionRequesterTest : DirectDebitsAcceptanceTest() {

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private val requester get() = getBean(DirectDebitCollectionRequester::class.java)

    private fun theCollectionRequesterRuns() = Action<ActionContext> { requester.requestDueCollections() }

    private fun expectedEndToEndIdFor(occurrenceDate: String): UUID =
        ScheduledPaymentEndToEndId.forOccurrence(someMandateUid(), LocalDate.parse(occurrenceDate))

    @Test
    fun dueActiveMandateRequestsACollectionWithADeterministicEndToEndIdAndAdvancesToTheNextOccurrence() =
        malaiAcceptanceTest {
            given(postCollectionBacsAccepts())
            and(anAccountAndDirectDebitExist(
                accountHolderId = someAccountHolderUid(),
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                debtorName = "Alice Walker",
                debtorSortCode = "65-82-37",
                debtorAccountNumber = "55667788",
                creditorName = "British Gas",
                creditorSortCode = "20-00-00",
                creditorAccountNumber = "12345678",
                reference = "Energy DD",
                amountMinorUnits = 4500,
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-06-01"),
                nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            ))
            and(nowIs("2026-06-01T09:00:00Z"))

            whenever(theCollectionRequesterRuns())

            then(directDebitServiceMadeTheFollowingHttpRequest {
                url == "/malai-stub/scenarios/collections/bacs"
            }) {
                method shouldBe POST
                body containsProp json {
                    "debtorSortCode" To "65-82-37"
                    "debtorAccountNumber" To "55667788"
                    "debtorName" To "Alice Walker"
                    "creditorSortCode" To "20-00-00"
                    "creditorAccountNumber" To "12345678"
                    "creditorName" To "British Gas"
                    "amountMinorUnits" To 4500
                    "currency" To "GBP"
                    "description" To "Energy DD"
                    "endToEndId" To expectedEndToEndIdFor("2026-06-01").toString()
                    noExtraProps()
                }
            }

            and(theDirectDebitInTheDatabase(someMandateUid())) {
                this shouldBe DirectDebitEntity(
                    directDebitId = someMandateUid(),
                    accountId = someAccountUid(),
                    creditorName = "British Gas",
                    creditorSortCode = "20-00-00",
                    creditorAccountNumber = "12345678",
                    reference = "Energy DD",
                    amountMinorUnits = 4500,
                    currency = Currency.GBP,
                    frequency = Frequency.MONTHLY,
                    status = InstrumentStatus.ACTIVE,
                    startDate = LocalDate.parse("2026-06-01"),
                    cancelledAt = null,
                    nextCollectionAt = Instant.parse("2026-07-01T00:00:00Z"),
                    createdAt = Instant.parse("2026-04-01T09:00:00Z"),
                )
            }
        }

    @Test
    fun aMandateOverdueBySeveralPeriodsCollectsExactlyOnceAndResynchronisesToTheFirstFutureOccurrence() =
        malaiAcceptanceTest {
            given(postCollectionBacsAccepts())
            and(anAccountAndDirectDebitExist(
                accountHolderId = someAccountHolderUid(),
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                debtorAccountNumber = "55667788",
                reference = "Energy DD",
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-01-01"),
                nextCollectionAt = Instant.parse("2026-01-01T00:00:00Z"),
            ))
            and(nowIs("2026-07-20T09:00:00Z"))

            whenever(theCollectionRequesterRuns())

            then(directDebitServiceMadeTheFollowingHttpRequests {
                url == "/malai-stub/scenarios/collections/bacs"
            }) {
                this shouldHaveSize 1
                this[0].body containsProp json {
                    "debtorSortCode" To "65-82-37"
                    "debtorAccountNumber" To "55667788"
                    "debtorName" To "Alice Walker"
                    "creditorSortCode" To "20-00-00"
                    "creditorAccountNumber" To "12345678"
                    "creditorName" To "British Gas"
                    "amountMinorUnits" To 4500
                    "currency" To "GBP"
                    "description" To "Energy DD"
                    "endToEndId" To expectedEndToEndIdFor("2026-01-01").toString()
                    noExtraProps()
                }
            }

            and(theDirectDebitInTheDatabase(someMandateUid())) {
                this!!.nextCollectionAt shouldBe Instant.parse("2026-08-01T00:00:00Z")
                this.status shouldBe InstrumentStatus.ACTIVE
            }
        }

    @Test
    fun aReFiredOccurrenceCarriesTheSameDeterministicEndToEndIdSoItDedupsHarmlesslyDownstream() =
        malaiAcceptanceTest {
            given(postCollectionBacsAccepts())
            and(anAccountAndDirectDebitExist(
                accountHolderId = someAccountHolderUid(),
                directDebitId = someMandateUid(),
                accountId = someAccountUid(),
                debtorAccountNumber = "55667788",
                reference = "Energy DD",
                frequency = Frequency.MONTHLY,
                startDate = LocalDate.parse("2026-06-01"),
                nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
            ))
            and(nowIs("2026-06-01T09:00:00Z"))

            whenever(theCollectionRequesterRuns())
            and(theDirectDebitNextCollectionIsResetTo(someMandateUid(), Instant.parse("2026-06-01T00:00:00Z")))
            whenever(theCollectionRequesterRuns())

            then(directDebitServiceMadeTheFollowingHttpRequests {
                url == "/malai-stub/scenarios/collections/bacs"
            }) {
                this shouldHaveSize 2
                this.forEach { request ->
                    request.body containsProp json {
                        "debtorSortCode" To "65-82-37"
                        "debtorAccountNumber" To "55667788"
                        "debtorName" To "Alice Walker"
                        "creditorSortCode" To "20-00-00"
                        "creditorAccountNumber" To "12345678"
                        "creditorName" To "British Gas"
                        "amountMinorUnits" To 4500
                        "currency" To "GBP"
                        "description" To "Energy DD"
                        "endToEndId" To expectedEndToEndIdFor("2026-06-01").toString()
                        noExtraProps()
                    }
                }
            }
        }
}
