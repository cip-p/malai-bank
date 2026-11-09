package com.malai.standingorder.repository

import com.malai.commons.model.InstrumentStatus
import com.malai.standingorder.acceptance.StandingOrdersAcceptanceTest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class StandingOrdersRepositoryTest : StandingOrdersAcceptanceTest() {

    private val accountA = UUID.fromString("00000000-0000-4000-8000-0000000000a1")
    private val accountB = UUID.fromString("00000000-0000-4000-8000-0000000000b2")
    private val accountC = UUID.fromString("00000000-0000-4000-8000-0000000000c3")

    @BeforeEach
    fun resetDatabase() = cleanTables()

    private fun dueScanAsOf(instant: String) =
        standingOrdersRepository().findDueActiveOrders(Instant.parse(instant))

    @Test
    fun findDueActiveOrdersReturnsActiveOrdersWhoseNextPaymentMomentHasArrived() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").map { it.standingOrderId } shouldBe listOf(someMandateUid())
    }

    @Test
    fun findDueActiveOrdersExcludesOrdersWhoseNextPaymentIsStillInTheFuture() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextPaymentAt = Instant.parse("2026-07-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveOrdersExcludesCancelledOrdersEvenWhenTheirNextPaymentHasPassed() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            status = InstrumentStatus.CANCELLED,
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
            nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveOrdersExcludesOrdersWithNoScheduledNextPayment() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            status = InstrumentStatus.ACTIVE,
            nextPaymentAt = null,
        ))

        dueScanAsOf("2026-06-01T09:00:00Z").shouldBeEmpty()
    }

    @Test
    fun findDueActiveOrdersReturnsDueOrdersOldestNextPaymentFirst() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = anotherMandateUid(),
            nextPaymentAt = Instant.parse("2026-06-15T00:00:00Z"),
        ))
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            nextPaymentAt = Instant.parse("2026-06-01T00:00:00Z"),
        ))

        dueScanAsOf("2026-06-20T09:00:00Z").map { it.standingOrderId } shouldBe
            listOf(someMandateUid(), anotherMandateUid())
    }

    @Test
    fun findActiveByAccountIdReturnsTheAccountsActiveStandingOrders() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = accountA,
            status = InstrumentStatus.ACTIVE,
        ))

        standingOrdersRepository().findActiveByAccountId(accountA).map { it.standingOrderId } shouldBe
            listOf(someMandateUid())
    }

    @Test
    fun findActiveByAccountIdExcludesCancelledStandingOrders() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = accountB,
            status = InstrumentStatus.CANCELLED,
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
        ))

        standingOrdersRepository().findActiveByAccountId(accountB).shouldBeEmpty()
    }

    @Test
    fun findActiveByAccountIdExcludesStandingOrdersOnOtherAccounts() = malaiAcceptanceTest {
        given(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = someMandateUid(),
            accountId = accountA,
            status = InstrumentStatus.ACTIVE,
        ))
        and(anAccountAndStandingOrderExist(
            accountHolderId = someAccountHolderUid(),
            standingOrderId = anotherMandateUid(),
            accountId = accountC,
            status = InstrumentStatus.ACTIVE,
        ))

        standingOrdersRepository().findActiveByAccountId(accountA).map { it.standingOrderId } shouldBe
            listOf(someMandateUid())
    }
}
