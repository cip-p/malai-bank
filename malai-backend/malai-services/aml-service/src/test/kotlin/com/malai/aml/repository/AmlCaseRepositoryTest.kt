package com.malai.aml.repository

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.aml.service.AmlCaseService.Companion.NON_TERMINAL_STATUSES
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

private val FIRST_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val SECOND_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c2")
private val THIRD_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c3")

private val FIRST_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa1")
private val SECOND_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa2")

private val LINKED_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000add1")
private val OTHER_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000add2")
private val UNLINKED_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000add9")

class AmlCaseRepositoryTest : AmlAcceptanceTest() {

    private fun caseRepository() = getBean(AmlCaseRepository::class.java)

    @Test
    fun findByAccountIdAndStatusInFindsTheAccountsNonTerminalCaseOnly() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(FIRST_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(OTHER_ALERT_ID), "2026-10-14T10:00:00Z"))
        and(anAmlCaseExists(SECOND_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(LINKED_ALERT_ID), "2026-10-15T10:00:00Z"))
        and(anAmlCaseExists(THIRD_CASE_ID, SECOND_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(OTHER_ALERT_ID), "2026-10-15T11:00:00Z"))

        caseRepository().findByAccountIdAndStatusIn(FIRST_ACCOUNT_ID, NON_TERMINAL_STATUSES)?.caseId shouldBe SECOND_CASE_ID
        caseRepository().findByAccountIdAndStatusIn(SECOND_ACCOUNT_ID, NON_TERMINAL_STATUSES)?.caseId shouldBe THIRD_CASE_ID
    }

    @Test
    fun findByAccountIdAndStatusInFindsNothingWhenTheAccountsOnlyCaseIsClosed() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(FIRST_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(OTHER_ALERT_ID), "2026-10-14T10:00:00Z"))

        caseRepository().findByAccountIdAndStatusIn(FIRST_ACCOUNT_ID, NON_TERMINAL_STATUSES).shouldBeNull()
    }

    @Test
    fun linksAlertDecisionSeesAlertsLinkedToAnyCaseIncludingClosedOnes() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(FIRST_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.CLOSED_REPORTED, listOf(OTHER_ALERT_ID), "2026-10-14T10:00:00Z"))
        and(anAmlCaseExists(SECOND_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(LINKED_ALERT_ID), "2026-10-15T10:00:00Z"))

        caseRepository().linksAlertDecision(LINKED_ALERT_ID) shouldBe true
        caseRepository().linksAlertDecision(OTHER_ALERT_ID) shouldBe true
        caseRepository().linksAlertDecision(UNLINKED_ALERT_ID) shouldBe false
    }

    @Test
    fun findAllByOrderByOpenedAtDescReturnsNewestOpenedFirst() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(FIRST_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(OTHER_ALERT_ID), "2026-10-13T10:00:00Z"))
        and(anAmlCaseExists(SECOND_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(LINKED_ALERT_ID), "2026-10-15T10:00:00Z"))
        and(anAmlCaseExists(THIRD_CASE_ID, SECOND_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(OTHER_ALERT_ID), "2026-10-14T10:00:00Z"))

        caseRepository().findAllByOrderByOpenedAtDesc().map { it.caseId } shouldBe
            listOf(SECOND_CASE_ID, THIRD_CASE_ID, FIRST_CASE_ID)
    }

    @Test
    fun findByStatusOrderByOpenedAtDescNarrowsToTheStatus() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(FIRST_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(OTHER_ALERT_ID), "2026-10-13T10:00:00Z"))
        and(anAmlCaseExists(SECOND_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(LINKED_ALERT_ID), "2026-10-15T10:00:00Z"))
        and(anAmlCaseExists(THIRD_CASE_ID, SECOND_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(OTHER_ALERT_ID), "2026-10-14T10:00:00Z"))

        caseRepository().findByStatusOrderByOpenedAtDesc(AmlCaseStatus.OPEN).map { it.caseId } shouldBe
            listOf(SECOND_CASE_ID, THIRD_CASE_ID)
    }
}
