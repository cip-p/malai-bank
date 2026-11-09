package com.malai.test.acceptance.kensa

import com.malai.test.acceptance.kensa.MalaiParty.Companion.Client
import com.malai.test.acceptance.kensa.MalaiParty.Companion.IdentityProvider
import com.malai.test.acceptance.kensa.MalaiParty.Companion.Unknown
import dev.kensa.state.Party

/**
 * A party (client / service / third party) on a Kensa sequence diagram.
 *
 * This shared module only pre-declares the universal parties ([Client], [IdentityProvider],
 * [OAuth2Service], [Unknown]) — each service should declare its own singleton parties for the
 * services it interacts with (e.g. its own party, and any downstream services it stubs via
 * WireMock).
 *
 * The identity surface is intentionally split in two so diagrams stay vendor-agnostic:
 *  - [IdentityProvider] — user management (register / login / verify-code / JWKS discovery)
 *  - [OAuth2Service] — client-credentials token issuance for service-to-service auth
 *
 * In production both are served by AWS Cognito; the names hide that detail from the diagram.
 */
class MalaiParty(private val name: String) : Party {
    override fun asString(): String = name

    override fun equals(other: Any?): Boolean = other is MalaiParty && other.name == name
    override fun hashCode(): Int = name.hashCode()

    companion object {
        val Client = MalaiParty("Client")
        val IdentityProvider = MalaiParty("IdentityProvider")
        val OAuth2Service = MalaiParty("OAuth2Service")
        val Unknown = MalaiParty("Unknown")

        // Pre-declared service parties. A service registers itself as the `fromSystem` in its
        // acceptance-test base class, and lists any downstream services it stubs in its
        // `toSystemLookup`. Add new services here as they're created.
        val MalaiApi = MalaiParty("MalaiApi")
        val AuthService = MalaiParty("AuthService")
        val AccountHolderService = MalaiParty("AccountHolderService")
        val AccountService = MalaiParty("AccountService")
        val SavingsGoalService = MalaiParty("SavingsGoalService")
        val TransactionFeedService = MalaiParty("TransactionFeedService")
        val DirectDebitService = MalaiParty("DirectDebitService")
        val PayeeService = MalaiParty("PayeeService")
        val PaymentsService = MalaiParty("PaymentsService")
        val StandingOrderService = MalaiParty("StandingOrderService")
        val LedgerService = MalaiParty("LedgerService")
        val CardService = MalaiParty("CardService")
        val InsightsService = MalaiParty("InsightsService")
        val FpsGateway = MalaiParty("FpsGateway")
        val SwiftGateway = MalaiParty("SwiftGateway")
        val BacsGateway = MalaiParty("BacsGateway")
        val CardGateway = MalaiParty("CardGateway")
        val MalaiStub = MalaiParty("MalaiStub")
        val SandboxService = MalaiParty("SandboxService")
        val MdrService = MalaiParty("MdrService")
        val ExecService = MalaiParty("ExecService")
        val CopilotService = MalaiParty("CopilotService")
        val AmlService = MalaiParty("AmlService")

        // Scheme counterparties on the payment rails, used as Kensa lanes for SQS interactions.
        val Fps = MalaiParty("FPS")
        val Swift = MalaiParty("SWIFT")
        val Bacs = MalaiParty("BACS")
        val CoP = MalaiParty("CoP")

        // The card network the stub impersonates when it presents/authorises against the card
        // gateway. Kept vendor-agnostic (like [IdentityProvider]) rather than naming Mastercard/Visa.
        val CardScheme = MalaiParty("CardScheme")

        // The cross-rail directory of external-bank accounts (sort code / account number / bank /
        // name-on-record) the stub serves from its fictional directory. Vendor-agnostic stand-in for
        // the industry counterparty/sort-code directory, so catalogue lookups don't read as MalaiStub.
        val CounterpartyDirectory = MalaiParty("CounterpartyDirectory")
    }
}
