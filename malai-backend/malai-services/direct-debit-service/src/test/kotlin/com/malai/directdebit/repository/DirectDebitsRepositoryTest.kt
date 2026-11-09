package com.malai.directdebit.repository

import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class DirectDebitsRepositoryTest : DirectDebitsAcceptanceTest() {

    private val accountA = java.util.UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    private val accountB = java.util.UUID.fromString("00000000-0000-4000-8000-0000000000b2")
    private val accountC = java.util.UUID.fromString("00000000-0000-4000-8000-0000000000c3")

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private fun dueScanAsOf(instant: String) =
        directDebitsRepository().findDueActiveMandates(Instant.parse(instant))

    @Test
    fun findDueActiveMandatesReturnsActiveMandatesWhoseNextCollectionMomentHasArrived() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").map { it.directDebitId } shouldBe listOf(someMandateUid())
    }

    @Test
    fun findDueActiveMandatesExcludesMandatesWhoseNextCollectionIsStillInTheFuture() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextCollectionAt = Instant.parse("2026-07-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveMandatesExcludesCancelledMandatesEvenWhenTheirNextCollectionHasPassed() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            status = InstrumentStatus.CANCELLED,
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
            nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveMandatesExcludesMandatesWithNoScheduledNextCollection() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextCollectionAt = null,
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveMandatesReturnsDueMandatesOldestNextCollectionFirst() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = anotherMandateUid(),
            nextCollectionAt = Instant.parse("2026-06-15T00:00:00Z"),
        ))
        and(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-20T09:00:00Z").map { it.directDebitId } shouldBe
            listOf(someMandateUid(), anotherMandateUid())
    }

    @Test
    fun findActiveByAccountIdReturnsTheAccountsActiveDirectDebits() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = accountA,
            status = InstrumentStatus.ACTIVE,
        ))

        directDebitsRepository().findActiveByAccountId(accountA).map { it.directDebitId } shouldBe
            listOf(someMandateUid())
    }

    @Test
    fun findActiveByAccountIdExcludesCancelledDirectDebits() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = accountB,
            status = InstrumentStatus.CANCELLED,
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
        ))

        directDebitsRepository().findActiveByAccountId(accountB).shouldBeEmpty()
    }

    @Test
    fun findActiveByAccountIdExcludesDirectDebitsOnOtherAccounts() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = accountA,
            debtorAccountNumber = "10000001",
            status = InstrumentStatus.ACTIVE,
        ))
        and(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = anotherMandateUid(),
            accountId = accountC,
            debtorAccountNumber = "10000003",
            status = InstrumentStatus.ACTIVE,
        ))

        directDebitsRepository().findActiveByAccountId(accountA).map { it.directDebitId } shouldBe
            listOf(someMandateUid())
    }

    @Test
    fun findDebtorSnapshotResolvesTheDebtorsRailDetailsForTheMandatesAccount() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = someAccountUid(),
            debtorName = "Alice Walker",
            debtorSortCode = "65-82-37",
            debtorAccountNumber = "55667788",
        ))

        val snapshot = directDebitsRepository().findDebtorSnapshot(someAccountUid())

        snapshot.shouldNotBeNull()
        snapshot.name shouldBe "Alice Walker"
        snapshot.sortCode shouldBe "65-82-37"
        snapshot.accountNumber shouldBe "55667788"
    }
}
