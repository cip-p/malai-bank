package com.malai.directdebit.acceptance.sad

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.directdebit.scheduler.DirectDebitCollectionRequester
import dev.kensa.Action
import dev.kensa.ActionContext
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class DirectDebitCollectionRequesterTest : DirectDebitsAcceptanceTest() {

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private val requester get() = getBean(DirectDebitCollectionRequester::class.java)

    private fun theCollectionRequesterRuns() = Action<ActionContext> { requester.requestDueCollections() }

    @Test
    fun cancelledMandatesAreNeverScannedEvenWhenTheirNextCollectionDateHasPassed() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            creditorName = "British Gas",
            reference = "Energy DD",
            amountMinorUnits = 4500,
            status = InstrumentStatus.CANCELLED,
            frequency = Frequency.MONTHLY,
            startDate = LocalDate.parse("2026-06-01"),
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
            nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
            createdAt = Instant.parse("2026-04-01T09:00:00Z"),
        ))
        and(nowIs("2026-06-01T09:00:00Z"))

        whenever(theCollectionRequesterRuns())

        then(directDebitServiceMadeTheFollowingHttpRequests {
            url == "/malai-stub/scenarios/collections/bacs"
        }) {
            this shouldHaveSize 0
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
                status = InstrumentStatus.CANCELLED,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
                nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-04-01T09:00:00Z"),
            )
        }
    }
}
