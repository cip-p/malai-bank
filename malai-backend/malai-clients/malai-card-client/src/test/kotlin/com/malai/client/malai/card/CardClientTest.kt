package com.malai.client.malai.card

import com.malai.client.malai.MalaiClientSpringTest
import com.malai.client.malai.card.builders.response.AccountAuthorisationResponseBuilder.Companion.anAccountAuthorisation
import com.malai.client.malai.card.builders.response.AccountAuthorisationsResponseBuilder.Companion.anAccountAuthorisations
import com.malai.client.malai.card.builders.response.AuthoriseCardResponseBuilder.Companion.anApprovedAuthorisation
import com.malai.client.malai.card.builders.response.CardDetailResponseBuilder.Companion.aCardDetailResponse
import com.malai.client.malai.card.builders.response.CardResponseBuilder.Companion.aCardResponse
import com.malai.client.malai.card.builders.response.CardsResponseBuilder.Companion.aCardsResponse
import com.malai.client.malai.card.builders.response.IssueCardResponseBuilder.Companion.anIssueCardResponse
import com.malai.client.malai.card.builders.response.StatementFiguresResponseBuilder.Companion.statementFigures
import com.malai.client.malai.card.model.AccountAuthorisationResponse
import com.malai.client.malai.card.model.AccountAuthorisationsResponse
import com.malai.client.malai.card.model.AuthorisationDecision.APPROVED
import com.malai.client.malai.card.model.AuthorisationStatus
import com.malai.client.malai.card.model.AuthoriseCardRequest
import com.malai.client.malai.card.model.AuthoriseCardResponse
import com.malai.client.malai.card.model.CardDetailResponse
import com.malai.client.malai.card.model.CardResponse
import com.malai.client.malai.card.model.CardAuthorisationStatus.SETTLED
import com.malai.client.malai.card.model.CardStatus.ACTIVE
import com.malai.client.malai.card.model.CardVariant.DEBIT
import com.malai.client.malai.card.model.CardsResponse
import com.malai.client.malai.card.model.DeclineReason.CARD_FROZEN
import com.malai.client.malai.card.model.InternalAuthorisationResponse
import com.malai.client.malai.card.model.InternalCardResponse
import com.malai.client.malai.card.model.IssueCardRequest
import com.malai.client.malai.card.model.IssueCardResponse
import com.malai.client.malai.card.model.StatementFiguresResponse
import com.malai.test.acceptance.MalaiClientTest
import com.neovibrant.kacey.matchers.ApiJsonMatcher.Companion.json
import dev.kensa.Action
import dev.kensa.ActionContext
import dev.kensa.StateCollector
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpMethod.POST
import java.util.UUID

@MalaiClientSpringTest
class CardClientTest : MalaiClientTest(), WithCards {

    @Autowired
    private lateinit var cardClient: CardClient

    private var lastIssueResponse: IssueCardResponse? = null
    private var lastCardsResponse: CardsResponse? = null
    private var lastCardDetailResponse: CardDetailResponse? = null
    private var lastAuthoriseResponse: AuthoriseCardResponse? = null
    private var lastAccountAuthorisationsResponse: AccountAuthorisationsResponse? = null
    private var lastStatementFiguresResponse: StatementFiguresResponse? = null
    private var lastResolveCardResponse: InternalCardResponse? = null
    private var lastResolveAuthorisationResponse: InternalAuthorisationResponse? = null

    @Test
    fun issueCard() {
        given(issueCardReturns(anIssueCardResponse()
            .withCardId(someCardUid())
            .withAccountId(someAccountUid())
            .withMaskedPan("************2341")
            .withVariant("DEBIT")
            .withStatus("ACTIVE")
            .withExpiry("10/29")))

        whenever(clientCallsIssueCard(
            "some-service-token",
            IssueCardRequest(
                accountId = someAccountUid(),
                accountHolderId = someAccountHolderUid(),
                cardholderName = "Sarah Mitchell",
                variant = DEBIT,
            )
        ))

        then(theLastIssueResponse()) {
            this shouldBe IssueCardResponse(someCardUid(), someAccountUid(), "************2341", DEBIT, ACTIVE, "10/29")
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "accountId" To someAccountUid().toString()
                "accountHolderId" To someAccountHolderUid().toString()
                "cardholderName" To "Sarah Mitchell"
                "variant" To "DEBIT"
                noExtraProps()
            }
        }
    }

    @Test
    fun authorise() {
        val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000a1")

        given(authoriseCardReturns(anApprovedAuthorisation()
            .withAuthorisationId(someCardUid())
            .withDecision("APPROVED")
            .withDeclineReason(null)))

        whenever(clientCallsAuthorise(
            "some-service-token",
            AuthoriseCardRequest(
                schemeReference = schemeReference,
                pan = "9904320000012341",
                amountMinorUnits = 4_370,
                currency = "GBP",
                merchantName = "Fictional Coffee Co",
                mcc = "5814",
            )
        ))

        then(theLastAuthoriseResponse()) {
            this shouldBe AuthoriseCardResponse(someCardUid(), APPROVED, null)
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations"
        }) {
            method shouldBe POST
            headers["Authorization"] shouldBe "Bearer some-service-token"
            headers["Content-Type"] shouldBe "application/json"
            body containsProp json {
                "schemeReference" To schemeReference.toString()
                "pan" To "9904320000012341"
                "amountMinorUnits" To 4_370
                "currency" To "GBP"
                "merchantName" To "Fictional Coffee Co"
                "mcc" To "5814"
                noExtraProps()
            }
        }
    }

    @Test
    fun listCards() {
        given(listCardsReturns(aCardsResponse().withCards(
            aCardResponse()
                .withCardId(someCardUid())
                .withAccountId(someAccountUid())
                .withMaskedPan("************2341")
                .withVariant("DEBIT")
                .withStatus("ACTIVE")
                .withExpiry("10/29"))))

        whenever(clientCallsListCards("some-access-token", someAccountHolderUid()))

        then(theLastCardsResponse()) {
            this shouldBe CardsResponse(listOf(
                CardResponse(someCardUid(), someAccountUid(), "************2341", DEBIT, ACTIVE, "10/29")
            ))
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/cards"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-access-token"
            headers["MAL-Account-Holder-Id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun getCard() {
        given(getCardReturns(aCardDetailResponse()
            .withCardId(someCardUid())
            .withAccountId(someAccountUid())
            .withCardholderName("Sarah Mitchell")
            .withPan("9904320000012341")
            .withExpiry("10/29")
            .withCvv("123")
            .withVariant("DEBIT")
            .withStatus("ACTIVE")))

        whenever(clientCallsGetCard("some-access-token", someAccountHolderUid(), someCardUid()))

        then(theLastCardDetailResponse()) {
            this shouldBe CardDetailResponse(
                someCardUid(), someAccountUid(), "Sarah Mitchell", "9904320000012341", "10/29", "123", DEBIT, ACTIVE,
            )
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/cards/${someCardUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-access-token"
            headers["MAL-Account-Holder-Id"] shouldBe someAccountHolderUid().toString()
        }
    }

    @Test
    fun listAccountAuthorisations() {
        val pendingId = UUID.fromString("00000000-0000-4000-7000-0000000000c1")
        val declinedId = UUID.fromString("00000000-0000-4000-7000-0000000000c2")

        given(listAccountAuthorisationsReturns(anAccountAuthorisations().withAuthorisations(
            anAccountAuthorisation()
                .withAuthorisationId(declinedId)
                .withCardId(someCardUid())
                .withStatus("DECLINED")
                .withMerchantName("Fictional Books Ltd")
                .withAmountMinorUnits(1_599)
                .withCurrency("GBP")
                .withDeclineReason("CARD_FROZEN")
                .withCreatedAt("2026-10-15T11:00:00Z"),
            anAccountAuthorisation()
                .withAuthorisationId(pendingId)
                .withCardId(someCardUid())
                .withStatus("AUTHORISED")
                .withMerchantName("Fictional Coffee Co")
                .withAmountMinorUnits(4_370)
                .withCurrency("GBP")
                .withDeclineReason(null)
                .withCreatedAt("2026-10-15T10:00:00Z"),
        )))

        whenever(clientCallsListAccountAuthorisations("some-service-token", someAccountUid()))

        then(theLastAccountAuthorisationsResponse()) {
            this shouldBe AccountAuthorisationsResponse(listOf(
                AccountAuthorisationResponse(
                    declinedId, someCardUid(), AuthorisationStatus.DECLINED, "Fictional Books Ltd",
                    1_599L, "GBP", CARD_FROZEN, "2026-10-15T11:00:00Z",
                ),
                AccountAuthorisationResponse(
                    pendingId, someCardUid(), AuthorisationStatus.AUTHORISED, "Fictional Coffee Co",
                    4_370L, "GBP", null, "2026-10-15T10:00:00Z",
                ),
            ))
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations?accountId=${someAccountUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun getStatementFigures() {
        given(getStatementFiguresReturns(statementFigures()
            .withStatementBalanceMinorUnits(40_000)
            .withMinimumPaymentMinorUnits(1_000)
            .withDueDate("2026-06-21")))

        whenever(clientCallsGetStatementFigures("some-service-token", -40_000, "2026-05"))

        then(theLastStatementFiguresResponse()) {
            this shouldBe StatementFiguresResponse(40_000, 1_000, "2026-06-21")
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/statement-figures?closingBalanceMinorUnits=-40000&cycleMonth=2026-05"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun resolveCard() {
        given(resolveCardReturns(
            cardId = someCardUid(),
            accountHolderId = someAccountHolderUid(),
            accountId = someAccountUid(),
            pan = "9904320000012341",
        ))

        whenever(clientCallsResolveCard("some-service-token", someCardUid(), someAccountHolderUid()))

        then(theLastResolveCardResponse()) {
            this shouldBe InternalCardResponse(someCardUid(), someAccountUid(), "9904320000012341")
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/${someCardUid()}?accountHolderId=${someAccountHolderUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun resolveCardReturnsNullWhenTheCardIsNotFound() {
        given(resolveCardReturnsNotFound(someCardUid(), someAccountHolderUid()))

        whenever(clientCallsResolveCard("some-service-token", someCardUid(), someAccountHolderUid()))

        then(theResolveCardOutcome()) { this shouldBe null }
    }

    @Test
    fun resolveAuthorisation() {
        val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")
        val schemeReference = UUID.fromString("00000000-0000-4000-8000-0000000000f2")

        given(resolveAuthorisationReturns(
            authorisationId = authorisationId,
            accountHolderId = someAccountHolderUid(),
            schemeReference = schemeReference,
            status = "SETTLED",
            amountMinorUnits = 4_370,
            currency = "GBP",
        ))

        whenever(clientCallsResolveAuthorisation("some-service-token", authorisationId, someAccountHolderUid()))

        then(theLastResolveAuthorisationResponse()) {
            this shouldBe InternalAuthorisationResponse(authorisationId, schemeReference, SETTLED, 4_370, "GBP")
        }
        and(madeTheFollowingHttpRequest {
            url == "/card-service/api/v1/internal/cards/authorisations/$authorisationId?accountHolderId=${someAccountHolderUid()}"
        }) {
            method shouldBe GET
            headers["Authorization"] shouldBe "Bearer some-service-token"
        }
    }

    @Test
    fun resolveAuthorisationReturnsNullWhenTheAuthorisationIsNotFound() {
        val authorisationId = UUID.fromString("00000000-0000-4000-7000-0000000000f1")

        given(resolveAuthorisationReturnsNotFound(authorisationId, someAccountHolderUid()))

        whenever(clientCallsResolveAuthorisation("some-service-token", authorisationId, someAccountHolderUid()))

        then(theResolveAuthorisationOutcome()) { this shouldBe null }
    }

    private fun clientCallsIssueCard(serviceToken: String, request: IssueCardRequest) =
        Action<ActionContext> { lastIssueResponse = cardClient.issueCard(serviceToken, request) }

    private fun clientCallsAuthorise(serviceToken: String, request: AuthoriseCardRequest) =
        Action<ActionContext> { lastAuthoriseResponse = cardClient.authorise(serviceToken, request) }

    private fun clientCallsListCards(accessToken: String, accountHolderId: UUID) =
        Action<ActionContext> { lastCardsResponse = cardClient.listCards(accessToken, accountHolderId) }

    private fun clientCallsGetCard(accessToken: String, accountHolderId: UUID, cardId: UUID) =
        Action<ActionContext> { lastCardDetailResponse = cardClient.getCard(accessToken, accountHolderId, cardId) }

    private fun clientCallsListAccountAuthorisations(serviceToken: String, accountId: UUID) =
        Action<ActionContext> { lastAccountAuthorisationsResponse = cardClient.listAccountAuthorisations(serviceToken, accountId) }

    private fun clientCallsGetStatementFigures(serviceToken: String, closingBalanceMinorUnits: Long, cycleMonth: String) =
        Action<ActionContext> { lastStatementFiguresResponse = cardClient.getStatementFigures(serviceToken, closingBalanceMinorUnits, cycleMonth) }

    private fun clientCallsResolveCard(serviceToken: String, cardId: UUID, accountHolderId: UUID) =
        Action<ActionContext> { lastResolveCardResponse = cardClient.resolveCard(serviceToken, cardId, accountHolderId) }

    private fun clientCallsResolveAuthorisation(serviceToken: String, authorisationId: UUID, accountHolderId: UUID) =
        Action<ActionContext> { lastResolveAuthorisationResponse = cardClient.resolveAuthorisation(serviceToken, authorisationId, accountHolderId) }

    private fun theLastIssueResponse() = StateCollector { _ -> lastIssueResponse ?: error("No issue-card response captured") }
    private fun theLastAuthoriseResponse() = StateCollector { _ -> lastAuthoriseResponse ?: error("No authorise response captured") }
    private fun theLastCardsResponse() = StateCollector { _ -> lastCardsResponse ?: error("No cards response captured") }
    private fun theLastCardDetailResponse() = StateCollector { _ -> lastCardDetailResponse ?: error("No card-detail response captured") }
    private fun theLastAccountAuthorisationsResponse() = StateCollector { _ -> lastAccountAuthorisationsResponse ?: error("No account-authorisations response captured") }
    private fun theLastStatementFiguresResponse() = StateCollector { _ -> lastStatementFiguresResponse ?: error("No statement-figures response captured") }
    private fun theLastResolveCardResponse() = StateCollector { _ -> lastResolveCardResponse ?: error("No resolve-card response captured") }
    private fun theLastResolveAuthorisationResponse() = StateCollector { _ -> lastResolveAuthorisationResponse ?: error("No resolve-authorisation response captured") }
    private fun theResolveCardOutcome() = StateCollector<InternalCardResponse?> { _ -> lastResolveCardResponse }
    private fun theResolveAuthorisationOutcome() = StateCollector<InternalAuthorisationResponse?> { _ -> lastResolveAuthorisationResponse }
}
