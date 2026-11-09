package com.malai.test.acceptance.kensa

import org.springframework.mock.web.MockHttpServletRequest

private const val UUID_PATTERN = "[0-9a-fA-F\\-]{36}"

class HttpClassifier {

    companion object {

        private val authClassifiers = listOf(
            Classifier("POST", "/api/v1/auth/register", "Register"),
            Classifier("POST", "/api/v1/auth/verify-code", "Verify Code"),
            Classifier("POST", "/api/v1/auth/login", "Login"),
            Classifier("POST", "/api/v1/auth/refresh-token", "Refresh Token"),
            Classifier("POST", "/api/v1/auth/signout", "Signout"),

            Classifier("GET", "/api/v1/user", "Get User"),
            Classifier("PUT", "/api/v1/user", "Update User"),

            Classifier("GET", "/api/v1/admin/audit-chain/verification", "Verify Audit Chain"),
        )

        private val accountsClassifiers = listOf(
            Classifier("GET", "/api/v1/accounts", "Get Accounts"),
            Classifier("POST", "/api/v1/accounts", "Create Account"),
            Classifier("POST", "/api/v1/credit-card-applications/$UUID_PATTERN", "Apply For Credit Card"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/balance", "Get Balance"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/identifiers", "Get Identifiers"),
            Classifier("PUT", "/api/v1/accounts/$UUID_PATTERN/transactions/$UUID_PATTERN/category", "Override Transaction Category"),
            Classifier("DELETE", "/api/v1/accounts/$UUID_PATTERN/transactions/$UUID_PATTERN/category", "Clear Transaction Category"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/transactions", "Get Transactions"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/spending", "Get Spending Breakdown"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/statements/\\d{4}-\\d{2}", "Get Statement"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/confirmation-of-funds", "Confirmation Of Funds"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/credit-summary", "Get Credit Summary"),
            Classifier("POST", "/api/v1/accounts/$UUID_PATTERN/transfers", "Execute Transfer"),
            Classifier("GET", "/api/v1/accounts/$UUID_PATTERN/feed-export", "Download Feed Export"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/spaces/spending/$UUID_PATTERN", "Get Spending Space"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/spaces/$UUID_PATTERN/photo", "Get Space Photo"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/spaces", "Get Spaces"),
            Classifier("POST", "/api/v1/internal/onboarding/accounts/activate/$UUID_PATTERN", "Activate Onboarding Accounts"),
            Classifier("POST", "/api/v1/internal/onboarding/accounts", "Create Onboarding Account"),
            Classifier("GET", "/api/v1/internal/accounts/by-identifier", "Resolve Internal Account By Identifier"),
            Classifier("GET", "/api/v1/internal/accounts/$UUID_PATTERN", "Get Internal Account"),
        )

        private val ledgerClassifiers = listOf(
            Classifier("POST", "/api/v1/internal/ledger/accounts", "Register Ledger Account"),
            Classifier("GET", "/api/v1/internal/ledger/accounts/$UUID_PATTERN/balances", "Get Ledger Balances"),
            Classifier("GET", "/api/v1/internal/ledger/accounts/$UUID_PATTERN/entries", "Get Ledger Entries"),
            Classifier("GET", "/api/v1/internal/ledger/holds/$UUID_PATTERN", "Get Ledger Hold"),
            Classifier("POST", "/api/v1/internal/ledger/holds/$UUID_PATTERN/settle", "Settle Ledger Hold"),
            Classifier("POST", "/api/v1/internal/ledger/holds/$UUID_PATTERN/release", "Release Ledger Hold"),
            Classifier("POST", "/api/v1/internal/ledger/holds", "Place Ledger Hold"),
            Classifier("GET", "/api/v1/internal/ledger/transactions/$UUID_PATTERN/entries", "Get Ledger Transaction Entries"),
            Classifier("POST", "/api/v1/internal/ledger/transactions", "Post Ledger Transaction"),
            Classifier("GET", "/api/v1/internal/ledger/transactions", "Get Ledger Transactions For Trace"),
        )

        private val accountHolderClassifiers = listOf(
            Classifier("POST", "/api/v1/account-holder", "Create Account Holder"),
            Classifier("POST", "/api/v1/account-holder/individual", "Create Account Holder Individual"),
            Classifier("GET", "/api/v1/account-holder/individual/email", "Get Email"),
            Classifier("PUT", "/api/v1/account-holder/individual/email", "Update Email"),
            Classifier("GET", "/api/v1/account-holder/individual", "Get Individual"),
            Classifier("GET", "/api/v1/account-holder/business/correspondence-address", "Get Correspondence Address"),
            Classifier("GET", "/api/v1/account-holder/business/registered-address", "Get Registered Address"),
            Classifier("GET", "/api/v1/account-holder/business", "Get Business"),
            Classifier("GET", "/api/v1/account-holder/joint", "Get Joint Account"),
            Classifier("GET", "/api/v1/account-holder/sole-trader", "Get Sole Trader"),
            Classifier("GET", "/api/v1/account-holder/name", "Get Account Holder Name"),
            Classifier("GET", "/api/v1/account-holder/$UUID_PATTERN/profile-image", "Get Profile Image"),
            Classifier("PUT", "/api/v1/account-holder/$UUID_PATTERN/profile-image", "Update Profile Image"),
            Classifier("DELETE", "/api/v1/account-holder/$UUID_PATTERN/profile-image", "Delete Profile Image"),
            Classifier("GET", "/api/v1/account-holder/all", "Get All Account Holders"),
            Classifier("GET", "/api/v1/account-holder", "Get Account Holder"),
            Classifier("GET", "/api/v1/addresses", "Get Addresses"),
            Classifier("POST", "/api/v1/addresses", "Update Address"),
            Classifier("GET", "/api/v1/settle-up/profile", "Get Settle Up Profile"),
            Classifier("POST", "/api/v1/internal/onboarding/account-holders/$UUID_PATTERN/individual", "Create Onboarding Individual"),
            Classifier("POST", "/api/v1/internal/onboarding/account-holders/$UUID_PATTERN/address", "Create Onboarding Address"),
            Classifier("POST", "/api/v1/internal/onboarding/account-holders/activate/$UUID_PATTERN", "Activate Onboarding Account Holder"),
            Classifier("POST", "/api/v1/internal/onboarding/account-holders", "Create Onboarding Account Holder"),
            Classifier("GET", "/api/v1/internal/account-holders/created-count", "Count Account Holders Created"),
        )

        private val transactionFeedClassifiers = listOf(
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/attachments/$UUID_PATTERN", "Download Feed Item Attachment"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/attachments", "Get Feed Item Attachments"),
            Classifier("POST", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/attachments", "Attach To Feed Item"),
            Classifier("PUT", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/spending-category", "Change Transaction Category"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/mastercard", "Get Mastercard Feed Item"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/receipts", "Get Receipts"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/receipt", "Get Receipt"),
            Classifier("PUT", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/receipt", "Create Or Update Receipt"),
            Classifier("PUT", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN/user-note", "Update User Note"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/$UUID_PATTERN", "Get Feed Item"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/transactions-between", "Query Feed Items Between"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN/paginated-transactions", "Query Paginated Feed Items"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/category/$UUID_PATTERN", "Query Feed Items Changes Since"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/settled-transactions-between", "Query Settled Transactions Between"),
        )

        private val savingsGoalClassifiers = listOf(
            Classifier("PUT", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/add-money/$UUID_PATTERN", "Add Money"),
            Classifier("PUT", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/withdraw-money/$UUID_PATTERN", "Withdraw Money"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/recurring-transfer", "Get Recurring Transfer"),
            Classifier("PUT", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/recurring-transfer", "Create Recurring Transfer"),
            Classifier("DELETE", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/recurring-transfer", "Delete Recurring Transfer"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN/photo", "Get Photo"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN", "Get Savings Goal"),
            Classifier("PUT", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN", "Update Savings Goal"),
            Classifier("DELETE", "/api/v1/account/$UUID_PATTERN/savings-goals/$UUID_PATTERN", "Delete Savings Goal"),
            Classifier("GET", "/api/v1/account/$UUID_PATTERN/savings-goals", "Get Savings Goals"),
            Classifier("PUT", "/api/v1/account/$UUID_PATTERN/savings-goals", "Create Savings Goal"),
        )

        private val cardClassifiers = listOf(
            Classifier("GET", "/api/v1/cards", "Get Cards"),
            Classifier("GET", "/api/v1/cards/$UUID_PATTERN", "Get Card"),
            Classifier("POST", "/api/v1/cards/$UUID_PATTERN/freeze", "Freeze Card"),
            Classifier("POST", "/api/v1/cards/$UUID_PATTERN/unfreeze", "Unfreeze Card"),
            Classifier("POST", "/api/v1/cards/$UUID_PATTERN/report-lost-or-stolen", "Report Card Lost Or Stolen"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/enabled", "Enable Card"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/atm-enabled", "Enable ATM"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/online-enabled", "Enable Online"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/mobile-wallet-enabled", "Enable Mobile Wallet"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/gambling-enabled", "Enable Gambling"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/mag-stripe-enabled", "Enable MagStripe"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/pos-enabled", "Enable POS"),
            Classifier("PUT", "/api/v1/cards/$UUID_PATTERN/controls/currency-switch", "Enable Currency Switch"),

            Classifier("GET", "/api/v1/internal/cards/authorisations/$UUID_PATTERN", "Get Card Authorisation"),
            Classifier("POST", "/api/v1/internal/cards/authorisations", "Authorise Card"),
            Classifier("GET", "/api/v1/internal/cards/authorisations", "List Account Authorisations"),
            Classifier("GET", "/api/v1/internal/cards/statement-figures", "Get Card Statement Figures"),
            Classifier("GET", "/api/v1/internal/cards/issued-count", "Count Cards Issued"),
            Classifier("GET", "/api/v1/internal/cards/$UUID_PATTERN", "Get Internal Card"),
            Classifier("POST", "/api/v1/internal/cards", "Issue Card"),
        )

        private val paymentLocalClassifiers = listOf(
            Classifier("GET", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders/$UUID_PATTERN/upcoming-payments", "Get Upcoming Payments"),
            Classifier("GET", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders/$UUID_PATTERN", "Get Standing Order"),
            Classifier("PUT", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders/$UUID_PATTERN", "Update Standing Order"),
            Classifier("DELETE", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders/$UUID_PATTERN", "Cancel Standing Order"),
            Classifier("GET", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders", "Get Standing Orders"),
            Classifier("PUT", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN/standing-orders", "Create Standing Order"),
            Classifier("PUT", "/api/v1/payments/local/account/$UUID_PATTERN/category/$UUID_PATTERN", "Create Payment"),
            Classifier("GET", "/api/v1/payments/local/payment-order/$UUID_PATTERN/payments", "Get Payment Order Payments"),
            Classifier("GET", "/api/v1/payments/local/payment-order/$UUID_PATTERN", "Get Payment Order"),
        )

        private val roundUpClassifiers = listOf(
            Classifier("PUT", "/round-up/[^/]+/$UUID_PATTERN", "Submit Round Up For Week"),
            Classifier("GET", "/round-up/[^/]+", "Get Round Up For Week"),
            Classifier("GET", "/api/v1/feed/account/$UUID_PATTERN/round-up", "Fetch Round Up Goal"),
            Classifier("PUT", "/api/v1/feed/account/$UUID_PATTERN/round-up", "Activate Round Up Goal"),
            Classifier("DELETE", "/api/v1/feed/account/$UUID_PATTERN/round-up", "Stop Round Up Goal"),
        )

        private val confirmationOfPayeeClassifiers = listOf(
            Classifier("POST", "/api/v1/confirmation-of-payee", "Confirmation Of Payee"),
        )

        private val paymentsClassifiers = listOf(
            Classifier("GET", "/api/v1/internal/payments/deposits-total", "Get Deposits Total"),
            Classifier("GET", "/api/v1/internal/payments/stopped-total", "Get Payments Stopped Total"),
            Classifier("GET", "/api/v1/internal/payments/$UUID_PATTERN", "Get Internal Payment"),
            Classifier("POST", "/api/v1/payments", "Initiate Payment"),
            Classifier("GET", "/api/v1/payments/$UUID_PATTERN", "Get Payment"),
        )

        private val payeeClassifiers = listOf(
            Classifier("GET", "/api/v1/payees/$UUID_PATTERN/account/$UUID_PATTERN/scheduled-payments", "List Scheduled Payments"),
            Classifier("GET", "/api/v1/payees/$UUID_PATTERN/account/$UUID_PATTERN/payments", "List Payments"),
            Classifier("DELETE", "/api/v1/payees/$UUID_PATTERN/account/$UUID_PATTERN", "Delete Payee Account"),
            Classifier("PUT", "/api/v1/payees/$UUID_PATTERN/account", "Create Payee Account"),
            Classifier("GET", "/api/v1/payees/$UUID_PATTERN/image", "Serve Payee Image"),
            Classifier("GET", "/api/v1/payees/$UUID_PATTERN", "Get Payee"),
            Classifier("PUT", "/api/v1/payees/$UUID_PATTERN", "Update Payee"),
            Classifier("DELETE", "/api/v1/payees/$UUID_PATTERN", "Delete Payee"),
            Classifier("GET", "/api/v1/payees", "Get Payees"),
            Classifier("POST", "/api/v1/payees", "Create Payee"),
            Classifier("PUT", "/api/v1/payees", "Create Payee"),
        )

        private val seedDemoDataClassifiers = listOf(
            Classifier("POST", "/api/v1/seed-demo-data", "Seed Demo Data"),
        )

        private val directDebitClassifiers = listOf(
            Classifier("GET", "/api/v1/direct-debit/mandates/$UUID_PATTERN/payments", "Get Mandate Payments"),
            Classifier("GET", "/api/v1/direct-debit/mandates/account/$UUID_PATTERN", "Get Mandates For Account"),
            Classifier("GET", "/api/v1/direct-debit/mandates/$UUID_PATTERN", "Get Mandate"),
            Classifier("DELETE", "/api/v1/direct-debit/mandates/$UUID_PATTERN", "Cancel Mandate"),
            Classifier("GET", "/api/v1/direct-debit/mandates", "Get Mandates"),
            Classifier("POST", "/api/v1/internal/direct-debits", "Lodge Direct Debit Mandate"),
            Classifier("DELETE", "/api/v1/direct-debits/$UUID_PATTERN", "Cancel Direct Debit"),
            Classifier("GET", "/api/v1/direct-debits", "List Direct Debits"),
        )

        private val standingOrderClassifiers = listOf(
            Classifier("DELETE", "/api/v1/standing-orders/$UUID_PATTERN", "Cancel Standing Order"),
            Classifier("POST", "/api/v1/standing-orders", "Create Standing Order"),
            Classifier("GET", "/api/v1/standing-orders", "List Standing Orders"),
        )

        private val mdrClassifiers = listOf(
            Classifier("GET", "/api/v1/internal/mdr/decisions/$UUID_PATTERN/actionable-output", "Get Actionable Output"),
            Classifier("GET", "/api/v1/internal/mdr/decisions/$UUID_PATTERN", "Get Decision Record"),
            Classifier("POST", "/api/v1/internal/mdr/decisions", "Record Model Decision"),
            Classifier("GET", "/api/v1/admin/mdr/chain-verification", "Verify MDR Chain"),
            Classifier("POST", "/api/v1/admin/mdr/decisions/$UUID_PATTERN/dispositions", "Record Human Disposition"),
            Classifier("GET", "/api/v1/admin/mdr/decisions/$UUID_PATTERN/actionable-output", "Get Actionable Output"),
            Classifier("GET", "/api/v1/admin/mdr/decisions/$UUID_PATTERN", "Get Decision Thread"),
            Classifier("GET", "/api/v1/admin/mdr/decisions", "List Decisions"),
        )

        private val amlClassifiers = listOf(
            Classifier("POST", "/api/v1/admin/aml/cases/$UUID_PATTERN/sar-draft", "Request SAR Draft"),
            Classifier("POST", "/api/v1/admin/aml/cases/$UUID_PATTERN/close-dismissed", "Close AML Case As Dismissed"),
            Classifier("POST", "/api/v1/admin/aml/cases/$UUID_PATTERN/close-reported", "Close AML Case As Reported"),
            Classifier("GET", "/api/v1/admin/aml/cases/$UUID_PATTERN", "Get AML Case"),
            Classifier("GET", "/api/v1/admin/aml/cases", "List AML Cases"),
        )

        private val execClassifiers = listOf(
            Classifier("GET", "/api/v1/exec/metrics", "Get Exec Metrics"),
        )

        private val copilotClassifiers = listOf(
            Classifier("POST", "/api/v1/admin/copilot/explanations", "Explain Subject"),
        )

        private val insightsClassifiers = listOf(
            Classifier("POST", "/api/v1/internal/insights/spending-breakdown", "Aggregate Spending Breakdown"),
            Classifier("POST", "/api/v1/internal/insights/categorisations", "Categorise Entries"),
            Classifier("PUT", "/api/v1/internal/insights/overrides/$UUID_PATTERN", "Set Category Override"),
            Classifier("DELETE", "/api/v1/internal/insights/overrides/$UUID_PATTERN", "Clear Category Override"),
        )

        private val malaiStubClassifiers = listOf(
            Classifier("POST", "/cop/lookup", "Lookup Confirmation Of Payee"),
            Classifier("POST", "/fps/payments", "Submit FPS Payment"),
            Classifier("POST", "/fps/returns", "Submit FPS Return"),
            Classifier("POST", "/swift/payments", "Submit SWIFT Payment"),
            Classifier("GET", "/scenarios/counterparties", "Get Counterparties Catalogue"),
            Classifier("POST", "/scenarios/inbound/fps", "Trigger FPS Inbound Scenario"),
            Classifier("POST", "/scenarios/inbound/bacs", "Trigger BACS Inbound Scenario"),
            Classifier("GET", "/scenarios/cards/merchants", "Get Card Merchants Catalogue"),
            Classifier("POST", "/scenarios/cards/pans", "Register Card PAN"),
            Classifier("GET", "/scenarios/cards/pans", "Get Registered Card PANs"),
            Classifier("POST", "/scenarios/cards/purchase", "Trigger Card Purchase Scenario"),
            Classifier("POST", "/scenarios/cards/present-now", "Trigger Card Presentment Scenario"),
            Classifier("POST", "/scenarios/cards/reverse", "Trigger Card Reversal Scenario"),
            Classifier("POST", "/scenarios/cards/refund", "Trigger Card Refund Scenario"),
            Classifier("POST", "/test-hooks/arm-return", "Arm FPS Return"),
            Classifier("GET", "/test-hooks/fps/returns", "Get Recorded FPS Returns"),
        )

        private val sandboxClassifiers = listOf(
            Classifier("GET", "/api/v1/sandbox/counterparties", "Get Sandbox Counterparties"),
            Classifier("POST", "/api/v1/sandbox/inbound-payments", "Trigger Sandbox Inbound Payment"),
            Classifier("POST", "/api/v1/sandbox/direct-debits", "Lodge Sandbox Direct Debit"),
            Classifier("GET", "/api/v1/sandbox/card-merchants", "Get Sandbox Card Merchants"),
            Classifier("POST", "/api/v1/sandbox/card-purchases", "Trigger Sandbox Card Purchase"),
            Classifier("POST", "/api/v1/sandbox/card-presentments", "Trigger Sandbox Card Presentment"),
            Classifier("POST", "/api/v1/sandbox/card-refunds", "Trigger Sandbox Card Refund"),
        )

        private val fpsGatewayClassifiers = listOf(
            Classifier("POST", "/fps/callbacks/settlement", "FPS Settlement Callback"),
            Classifier("POST", "/fps/callbacks/return", "FPS Return Callback"),
            Classifier("POST", "/fps/callbacks/inbound", "FPS Inbound Callback"),
        )

        private val bacsGatewayClassifiers = listOf(
            Classifier("POST", "/bacs/callbacks/inbound", "BACS Inbound Callback"),
        )

        private val swiftGatewayClassifiers = listOf(
            Classifier("POST", "/swift/callbacks/settlement", "SWIFT Settlement Callback"),
        )

        private val cardGatewayClassifiers = listOf(
            Classifier("POST", "/cards/callbacks/authorise", "Card Authorisation Callback"),
            Classifier("POST", "/cards/callbacks/present", "Card Presentment Callback"),
            Classifier("POST", "/cards/callbacks/reverse", "Card Reversal Callback"),
            Classifier("POST", "/cards/callbacks/refund", "Card Refund Callback"),
        )

        private val identityProviderClassifiers = listOf(
            Classifier("GET", "/cognito/.well-known/jwks.json", "Get JWKS"),
            Classifier("POST", "/oauth2/token", "Get Service Token"),
            Classifier("POST", "/", "Authenticate User", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.InitiateAuth"),
            Classifier("POST", "/", "Respond To Auth Challenge", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.RespondToAuthChallenge"),
            Classifier("POST", "/", "Get User Profile", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.GetUser"),
            Classifier("POST", "/", "Update User Attributes", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.UpdateUserAttributes"),
            Classifier("POST", "/", "Register User", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.SignUp"),
            Classifier("POST", "/", "Confirm Registration", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.ConfirmSignUp"),
            Classifier("POST", "/", "Resend Confirmation Code", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.ResendConfirmationCode"),
            Classifier("POST", "/", "Sign Out", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.GlobalSignOut"),
            Classifier("POST", "/", "Delete User (Admin)", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.AdminDeleteUser"),
            Classifier("POST", "/", "Add User To Group (Admin)", headerName = "X-Amz-Target", headerValue = "AWSCognitoIdentityProviderService.AdminAddUserToGroup"),
        )

        private val classifiers = identityProviderClassifiers +
            accountsClassifiers +
            accountHolderClassifiers +
            ledgerClassifiers +
            transactionFeedClassifiers +
            savingsGoalClassifiers +
            authClassifiers +
            cardClassifiers +
            paymentLocalClassifiers +
            paymentsClassifiers +
            roundUpClassifiers +
            confirmationOfPayeeClassifiers +
            payeeClassifiers +
            directDebitClassifiers +
            standingOrderClassifiers +
            insightsClassifiers +
            mdrClassifiers +
            amlClassifiers +
            execClassifiers +
            copilotClassifiers +
            malaiStubClassifiers +
            sandboxClassifiers +
            fpsGatewayClassifiers +
            bacsGatewayClassifiers +
            swiftGatewayClassifiers +
            cardGatewayClassifiers +
            seedDemoDataClassifiers

        fun classifies(request: MockHttpServletRequest): String {
            val method = request.method ?: return "Unknown"
            val requestURI = request.requestURI ?: return "Unknown"
            return classifies(method, requestURI)
        }

        fun classifies(method: String, uri: String): String =
            classifies(method, uri, emptyMap())

        fun classifies(method: String, uri: String, headers: Map<String, String>): String =
            classifiers
                .firstOrNull { it.matches(method, uri, headers) }
                ?.description
                ?: "Unknown"
    }

    private data class Classifier(
        val method: String,
        val uriPattern: String,
        val description: String,
        val headerName: String? = null,
        val headerValue: String? = null,
    ) {
        private val regex = Regex("^$uriPattern$")

        fun matches(method: String, uri: String, headers: Map<String, String> = emptyMap()): Boolean {
            if (this.method != method) return false
            if (!regex.matches(uri)) return false
            if (headerName != null) {
                val actual = headers.entries.firstOrNull { it.key.equals(headerName, ignoreCase = true) }?.value
                if (actual != headerValue) return false
            }
            return true
        }
    }
}
