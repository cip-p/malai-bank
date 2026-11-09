package com.malai.directdebit.acceptance.happy

import com.malai.commons.model.Currency
import com.malai.commons.model.Frequency
import com.malai.commons.model.InstrumentStatus
import com.malai.directdebit.acceptance.DirectDebitsAcceptanceTest
import com.malai.directdebit.entity.DirectDebitEntity
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.HttpStatus.OK
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * SO/DD lifecycle issue 09 — the internal mandate-lodgement endpoint reached by sandbox-service
 * through `DirectDebitLodgementClient`. Service-token authed; the sandbox has already resolved and
 * ownership-checked the account and snapshotted the creditor, so this endpoint owns only the row
 * write, the anchor-derived next collection, and the SYSTEM audit event.
 */
class InternalDirectDebitsControllerTest : DirectDebitsAcceptanceTest() {

    private val accountId = UUID.fromString("00000000-0000-4000-8000-0000000000d4")

    @BeforeEach
    fun resetDatabase() = cleanTables()

    @Test
    fun returnsTheAccountsActiveDirectDebits() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = accountId,
            creditorName = "British Gas",
            amountMinorUnits = 4_500,
            frequency = Frequency.MONTHLY,
            status = InstrumentStatus.ACTIVE,
            startDate = LocalDate.parse("2026-06-01"),
        ))

        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/internal/direct-debits")
                .queryParam("accountId", accountId)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To listOf(
                    json {
                        "directDebitId" To someMandateUid().toString()
                        "accountId" To accountId.toString()
                        "creditorName" To "British Gas"
                        "amountMinorUnits" To 4_500
                        "currency" To "GBP"
                        "frequency" To "MONTHLY"
                        "status" To "ACTIVE"
                        "startDate" To "2026-06-01"
                        noExtraProps()
                    },
                )
                noExtraProps()
            }
        }
    }

    @Test
    fun excludesCancelledDirectDebits() = malaiAcceptanceTest {
        given(anAccountAndDirectDebitExist(
            accountHolderId = someAccountHolderUid(),
            directDebitId = someMandateUid(),
            accountId = accountId,
            status = InstrumentStatus.CANCELLED,
            cancelledAt = Instant.parse("2026-05-20T09:00:00Z"),
        ))

        whenever(directDebitServiceReceives(
            get()
                .url("/api/v1/internal/direct-debits")
                .queryParam("accountId", accountId)
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "directDebits" To emptyList<Any>()
                noExtraProps()
            }
        }
    }

    @Test
    fun lodgeCreatesActiveMandateWithAnchoredNextCollectionAndSystemAuditRow() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(anAccountExists(accountHolderId = someAccountHolderUid(), accountId = someAccountUid()))
        and(theNextGeneratedUuidWillBe(someMandateUid()))
        and(nowIs("2026-05-10T10:30:00Z"))

        whenever(directDebitServiceReceives(
            post()
                .url("/api/v1/internal/direct-debits")
                .header("Content-Type", "application/json")
                .body(aLodgeRequestBody(startDate = "2026-06-01"))
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe CREATED
            body containsProp json {
                "directDebitId" To someMandateUid().toString()
                "accountId" To someAccountUid().toString()
                "creditorName" To "British Gas"
                "creditorSortCode" To "20-00-00"
                "creditorAccountNumber" To "12345678"
                "reference" To "DD-GAS"
                "amountMinorUnits" To 4500
                "currency" To "GBP"
                "frequency" To "MONTHLY"
                "status" To "ACTIVE"
                "startDate" To "2026-06-01"
                "nextCollectionAt" To "2026-06-01T00:00:00Z"
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
                reference = "DD-GAS",
                amountMinorUnits = 4500,
                currency = Currency.GBP,
                frequency = Frequency.MONTHLY,
                status = InstrumentStatus.ACTIVE,
                startDate = LocalDate.parse("2026-06-01"),
                cancelledAt = null,
                nextCollectionAt = Instant.parse("2026-06-01T00:00:00Z"),
                createdAt = Instant.parse("2026-05-10T10:30:00Z"),
            )
        }

        and(theAuditEventsInTheDatabase()) {
            this shouldHaveSize 1
            this[0]["action"] shouldBe "DIRECT_DEBIT_MANDATE_LODGED"
            this[0]["actor_type"] shouldBe "SYSTEM"
            this[0]["resource_type"] shouldBe "DirectDebit"
            this[0]["resource_id"] shouldBe someMandateUid().toString()
        }
    }

    private fun aLodgeRequestBody(startDate: String): String =
        """{"accountId":"${someAccountUid()}","creditorName":"British Gas","creditorSortCode":"20-00-00","creditorAccountNumber":"12345678","reference":"DD-GAS","amountMinorUnits":4500,"currency":"GBP","frequency":"MONTHLY","startDate":"$startDate"}"""
}
