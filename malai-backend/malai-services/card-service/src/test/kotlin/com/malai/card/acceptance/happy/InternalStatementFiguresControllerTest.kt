package com.malai.card.acceptance.happy

import com.malai.card.acceptance.CardAcceptanceTest
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.get
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK

class InternalStatementFiguresControllerTest : CardAcceptanceTest() {

    @Test
    fun derivesTheStatementBalanceMinimumPaymentAndDueDateFromTheClosingPositionAndCycle() = malaiAcceptanceTest {
        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/statement-figures")
                .queryParam("closingBalanceMinorUnits", "-40000")
                .queryParam("cycleMonth", "2026-05")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "statementBalanceMinorUnits" To 40_000
                "minimumPaymentMinorUnits" To 1_000
                "dueDate" To "2026-06-21"
                noExtraProps()
            }
        }
    }

    @Test
    fun reportsNothingOwedAndAZeroMinimumForAFullyRepaidCreditAccount() = malaiAcceptanceTest {
        whenever(cardServiceReceives(
            get()
                .url("/api/v1/internal/cards/statement-figures")
                .queryParam("closingBalanceMinorUnits", "0")
                .queryParam("cycleMonth", "2026-11")
                .withServiceJwtToken()
        ))

        then(theLastResponseHas()) {
            statusCode shouldBe OK
            body containsProp json {
                "statementBalanceMinorUnits" To 0
                "minimumPaymentMinorUnits" To 0
                "dueDate" To "2026-12-21"
                noExtraProps()
            }
        }
    }
}
