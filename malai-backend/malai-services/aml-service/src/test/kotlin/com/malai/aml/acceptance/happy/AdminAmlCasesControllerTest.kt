package com.malai.aml.acceptance.happy

import com.malai.aml.acceptance.AmlAcceptanceTest
import com.malai.aml.model.AmlCaseStatus
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import java.util.UUID

private val SOME_ADMIN_USER_ID: UUID = UUID.fromString("ad000000-0000-4000-9000-000000000001")

private val OPEN_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c1")
private val INVESTIGATING_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c2")
private val DISMISSED_CASE_ID: UUID = UUID.fromString("00000000-0000-4000-9000-0000000000c3")

private val FIRST_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa1")
private val SECOND_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa2")
private val THIRD_ACCOUNT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-000000000aa3")

private val FIRST_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000add1")
private val SECOND_ALERT_ID: UUID = UUID.fromString("00000000-0000-4000-9000-00000000add2")

class AdminAmlCasesControllerTest : AmlAcceptanceTest() {

    @Test
    fun theWorklistListsEveryCaseNewestOpenedFirst() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(OPEN_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(FIRST_ALERT_ID, SECOND_ALERT_ID), "2026-10-15T10:00:00Z"))
        and(anAmlCaseExists(INVESTIGATING_CASE_ID, SECOND_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(FIRST_ALERT_ID), "2026-10-14T10:00:00Z"))
        and(anAmlCaseExists(DISMISSED_CASE_ID, THIRD_ACCOUNT_ID, AmlCaseStatus.CLOSED_DISMISSED, listOf(SECOND_ALERT_ID), "2026-10-13T10:00:00Z"))

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cases" To listOf(
                    json {
                        "caseId" To OPEN_CASE_ID.toString()
                        "accountId" To FIRST_ACCOUNT_ID.toString()
                        "status" To "OPEN"
                        "alertCount" To 2
                        "openedAt" To "2026-10-15T10:00:00Z"
                        "updatedAt" To "2026-10-15T10:00:00Z"
                        noExtraProps()
                    },
                    json {
                        "caseId" To INVESTIGATING_CASE_ID.toString()
                        "accountId" To SECOND_ACCOUNT_ID.toString()
                        "status" To "INVESTIGATING"
                        "alertCount" To 1
                        "openedAt" To "2026-10-14T10:00:00Z"
                        "updatedAt" To "2026-10-14T10:00:00Z"
                        noExtraProps()
                    },
                    json {
                        "caseId" To DISMISSED_CASE_ID.toString()
                        "accountId" To THIRD_ACCOUNT_ID.toString()
                        "status" To "CLOSED_DISMISSED"
                        "alertCount" To 1
                        "openedAt" To "2026-10-13T10:00:00Z"
                        "updatedAt" To "2026-10-13T10:00:00Z"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun theWorklistNarrowsToOneLifecycleState() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())
        and(anAmlCaseExists(OPEN_CASE_ID, FIRST_ACCOUNT_ID, AmlCaseStatus.OPEN, listOf(FIRST_ALERT_ID), "2026-10-15T10:00:00Z"))
        and(anAmlCaseExists(INVESTIGATING_CASE_ID, SECOND_ACCOUNT_ID, AmlCaseStatus.INVESTIGATING, listOf(SECOND_ALERT_ID), "2026-10-14T10:00:00Z"))

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases")
                .queryParam("status", "INVESTIGATING")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cases" To listOf(
                    json {
                        "caseId" To INVESTIGATING_CASE_ID.toString()
                        "accountId" To SECOND_ACCOUNT_ID.toString()
                        "status" To "INVESTIGATING"
                        "alertCount" To 1
                        "openedAt" To "2026-10-14T10:00:00Z"
                        "updatedAt" To "2026-10-14T10:00:00Z"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun anEmptyWorklistIsAnEmptyList() = malaiAcceptanceTest {
        given(thereAreNoAmlCasesInTheDatabase())

        whenever(amlServiceReceives(
            get()
                .url("/api/v1/admin/aml/cases")
                .withJwtTokenForUserId(SOME_ADMIN_USER_ID, listOf("admin")),
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "cases" To emptyList<String>()
                noExtraProps()
            }
        }
    }
}
