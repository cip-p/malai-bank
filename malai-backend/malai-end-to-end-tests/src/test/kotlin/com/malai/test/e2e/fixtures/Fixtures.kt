package com.malai.test.e2e.fixtures

import com.malai.client.malai.MalaiTestFixtures
import com.malai.security.SecurityConfig.Companion.ACCOUNT_HOLDER_ID_HEADER
import com.malai.security.SecurityConfig.Companion.AUTHORIZATION_HEADER
import com.malai.security.SecurityConfig.Companion.BEARER_PREFIX
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.testingObjectMapper
import com.malai.test.e2e.MalaiE2ETestContext
import com.malai.test.e2e.WithCognito
import com.malai.test.e2e.fixtures.AccountHolderIndividualBuilderFixture.Companion.anAccountHolderIndividual
import com.malai.test.e2e.fixtures.CaptureType.*
import com.malai.test.e2e.fixtures.FixtureCapture.capture
import com.malai.test.e2e.fixtures.FixtureCapture.capturedUuid
import org.springframework.http.MediaType.APPLICATION_JSON
import java.util.*


class UserBuilderFixture private constructor() : WithCognito, MalaiTestFixtures {

    private lateinit var userId: UUID
    private lateinit var email: String
    private lateinit var firstName: String
    private lateinit var lastName: String
    private lateinit var dateOfBirth: String
    private var accountHolder: AccountHolderBuilderFixture? = null
    private var account: AccountBuilderFixture? = null

    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL

    fun withUserId(id: UUID) = apply { this.userId = id }
    fun withEmail(email: String) = apply { this.email = email }
    fun withFirstName(firstName: String) = apply { this.firstName = firstName }
    fun withLastName(lastName: String) = apply { this.lastName = lastName }
    fun withDateOfBirth(dateOfBirth: String) = apply { this.dateOfBirth = dateOfBirth }
    fun withAccountHolder(accountHolder: AccountHolderBuilderFixture) = apply { this.accountHolder = accountHolder }
    fun withAccount(account: AccountBuilderFixture) = apply { this.account = account }

    fun thatIsRegistered() = CallableAction {
        cognitoCanRegisterUser(email, userId).execute()
        cognitoCanIssueServiceToken().execute()

        MalaiE2ETestContext.preconditionsAuthServiceHttpClient
            .post()
            .uri("/api/v1/auth/register")
            .contentType(APPLICATION_JSON)
            .body(testingObjectMapper.writeValueAsString(mapOf(
                "firstName" to firstName,
                "lastName" to lastName,
                "dateOfBirth" to dateOfBirth,
                "email" to email,
                "address" to mapOf(
                    "line1" to "10 Downing Street",
                    "line2" to null,
                    "city" to "London",
                    "postcode" to "SW1A 2AA",
                    "country" to "GB",
                ),
            )))
            .retrieve()
            .toBodilessEntity()

        accountHolder
            ?.withUserId(userId)
            ?.thatExists()

        account
            ?.withUserId(userId)
            ?.withAccountHolderId(capturedUuid(ACCOUNT_HOLDER_ID))
            ?.thatExists()

        capture(USER_ID, userId)
        capture(USER_EMAIL, email)
    }

    private fun withDefaults() = apply {
        withUserId(someUserId())
        withEmail(someEmail())
        withFirstName("Alice")
        withLastName("Smith")
        withDateOfBirth("1990-04-12")
    }

    companion object {
        fun anUser() = UserBuilderFixture()
            .withDefaults()
    }
}

class AccountHolderBuilderFixture private constructor() : WithCognito, MalaiTestFixtures {

    private lateinit var userId: UUID
    private lateinit var accountHolderType: String
    private var accountHolderIndividual: AccountHolderIndividualBuilderFixture? = null

    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL

    fun withUserId(id: UUID) = apply { this.userId = id }
    fun withAccountHolderType(type: String) = apply { this.accountHolderType = type }
    fun withAccountHolderIndividual(accountHolderIndividual: AccountHolderIndividualBuilderFixture?) = apply { this.accountHolderIndividual = accountHolderIndividual }

    fun thatExists() {
        val response = MalaiE2ETestContext.preconditionsAccountHolderServiceHttpClient
            .post()
            .uri("/api/v1/account-holder")
            .contentType(APPLICATION_JSON)
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
            .body(jsonBodyOf("accountHolderType" to accountHolderType))
            .retrieve()
            .body(Map::class.java)!!

        val accountHolderId = UUID.fromString(response["accountHolderUid"] as String)
        capture(ACCOUNT_HOLDER_ID, accountHolderId)

        accountHolderIndividual
            ?.withAccountHolderId(accountHolderId)
            ?.withUserId(userId)
            ?.thatExists()
    }

    private fun withDefaults() = apply {
        withUserId(someUserId())
    }

    companion object {
        fun anIndividualAccountHolder() = AccountHolderBuilderFixture()
            .withDefaults()
            .withAccountHolderType("INDIVIDUAL")
            .withAccountHolderIndividual(anAccountHolderIndividual())
    }
}

class AccountHolderIndividualBuilderFixture private constructor() : WithCognito, MalaiTestFixtures {

    private lateinit var individualId: UUID
    private lateinit var accountHolderId: UUID
    private lateinit var userId: UUID
    private var title: String? = null
    private lateinit var firstName: String
    private lateinit var lastName: String
    private lateinit var dateOfBirth: String
    private lateinit var email: String
    private lateinit var phone: String

    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL

    fun withIndividualId(id: UUID) = apply { this.individualId = id }
    fun withAccountHolderId(id: UUID) = apply { this.accountHolderId = id }
    fun withUserId(id: UUID) = apply { this.userId = id }
    fun withTitle(title: String) = apply { this.title = title }
    fun withFirstName(name: String) = apply { this.firstName = name }
    fun withLastName(name: String) = apply { this.lastName = name }
    fun withDateOfBirth(dateOfBirth: String) = apply { this.dateOfBirth = dateOfBirth }
    fun withEmail(email: String) = apply { this.email = email }
    fun withPhone(phone: String) = apply { this.phone = phone }

    fun thatExists() {
        MalaiE2ETestContext.preconditionsAccountHolderServiceHttpClient
            .post()
            .uri("/api/v1/account-holder/individual")
            .contentType(APPLICATION_JSON)
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
            .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
            .body(jsonBodyOfNullable(
                "title" to title,
                "firstName" to firstName,
                "lastName" to lastName,
                "dateOfBirth" to dateOfBirth,
                "email" to email,
                "phone" to phone,
            ))
            .retrieve()
            .toBodilessEntity()
    }

    private fun withDefaults() = apply {
        withIndividualId(someIndividualUid())
        withAccountHolderId(someAccountHolderUid())
        withTitle("Mr")
        withFirstName("default-first-name")
        withLastName("default-last-name")
        withDateOfBirth("1990-01-01")
        withEmail("default-email@example.com")
        withPhone("default-phone")
    }

    companion object {
        fun anAccountHolderIndividual() = AccountHolderIndividualBuilderFixture()
            .withDefaults()
    }
}

class AccountBuilderFixture private constructor() : WithCognito, MalaiTestFixtures {

    private lateinit var userId: UUID
    private lateinit var accountHolderId: UUID
    private lateinit var accountType: String
    private lateinit var currency: String
    private lateinit var name: String

    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL

    fun withUserId(id: UUID) = apply { this.userId = id }
    fun withAccountHolderId(id: UUID) = apply { this.accountHolderId = id }
    fun withAccountType(type: String) = apply { this.accountType = type }
    fun withCurrency(currency: String) = apply { this.currency = currency }
    fun withName(name: String) = apply { this.name = name }

    fun thatExists() {
        // D-2.5: balance projects from ledger_entries — POST /deposit is gone.
        // Seeding starting balances now happens via the seeder (Plan 02-15) or via
        // TransfersController (Plan 02-12); fixtures stop here at account creation.
        val response = MalaiE2ETestContext.preconditionsAccountServiceHttpClient
            .post()
            .uri("/api/v1/accounts")
            .contentType(APPLICATION_JSON)
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
            .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
            .body(jsonBodyOf("accountType" to accountType, "currency" to currency, "name" to name))
            .retrieve()
            .body(Map::class.java)!!

        val accountUid = UUID.fromString(response["accountUid"] as String)
        capture(ACCOUNT_ID, accountUid)
    }

    private fun withDefaults() = apply {
        withAccountType("PRIMARY")
        withCurrency("GBP")
        withName("Personal")
    }

    companion object {
        fun anAccount() = AccountBuilderFixture()
            .withDefaults()
    }
}

/**
 * Creates an additional account on an already-onboarded holder via the preconditions client
 * (so it does NOT show up on the Kensa sequence diagram). Returns the new account's UID.
 *
 * Used by tests that need more than one account on the same holder (e.g. a Book Transfer between
 * the customer's own PRIMARY and SAVINGS accounts).
 */
fun createPreconditionAccount(
    userId: UUID,
    accountHolderId: UUID,
    accountType: String,
    name: String,
    currency: String = "GBP",
): UUID = object : WithCognito, MalaiTestFixtures {
    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL
}.run {
    val response = MalaiE2ETestContext.preconditionsAccountServiceHttpClient
        .post()
        .uri("/api/v1/accounts")
        .contentType(APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
        .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
        .body(testingObjectMapper.writeValueAsString(mapOf(
            "accountType" to accountType,
            "currency" to currency,
            "name" to name,
        )))
        .retrieve()
        .body(Map::class.java)!!
    UUID.fromString(response["accountUid"] as String)
}

/**
 * Creates a saved payee on an already-onboarded holder via the preconditions client (so it does
 * NOT show up on the Kensa sequence diagram). Returns the new payee's UID.
 *
 * Used by the standing-order e2e ride, whose create surface only accepts a saved `payeeId` — the
 * payee's name/sort-code/account-number are snapshotted onto the order. Keeping the payee off the
 * diagram leaves the StandingOrderTest sequence focused on the standing-order interactions.
 */
fun createPreconditionPayee(
    userId: UUID,
    accountHolderId: UUID,
    name: String,
    sortCode: String,
    accountNumber: String,
    idempotencyKey: String,
    nickname: String? = null,
): UUID = object : WithCognito, MalaiTestFixtures {
    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL
}.run {
    val response = MalaiE2ETestContext.preconditionsPayeeServiceHttpClient
        .post()
        .uri("/api/v1/payees")
        .contentType(APPLICATION_JSON)
        .header("Idempotency-Key", idempotencyKey)
        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
        .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
        .body(testingObjectMapper.writeValueAsString(mapOf(
            "name" to name,
            "sortCode" to sortCode,
            "accountNumber" to accountNumber,
            "nickname" to nickname,
        )))
        .retrieve()
        .body(Map::class.java)!!
    UUID.fromString(response["payeeId"] as String)
}

/**
 * Reads back the auto-generated SORT_CODE / account-number identifier for an account via the
 * preconditions client (so it does NOT show up on the Kensa sequence diagram). Account
 * identifiers are minted by [com.malai.accounts.identifier.AccountIdentifierFactory] when an
 * account is created; tests that need to drive a payment to a specific account use this helper
 * to discover the identifier without hardcoding the bank's sort code.
 */
fun readAccountSortCodeAndNumber(
    accountId: UUID,
    userId: UUID,
    accountHolderId: UUID,
): Pair<String, String> = object : WithCognito, MalaiTestFixtures {
    override fun identityProviderIssuerUrl(): String = MalaiE2ETestContext.COGNITO_ISSUER_URL
}.run {
    val response = MalaiE2ETestContext.preconditionsAccountServiceHttpClient
        .get()
        .uri("/api/v1/accounts/$accountId/identifiers")
        .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX${generateAccessTokenForUserId(userId)}")
        .header(ACCOUNT_HOLDER_ID_HEADER, accountHolderId.toString())
        .retrieve()
        .body(Map::class.java)!!
    (response["bankIdentifier"] as String) to (response["accountIdentifier"] as String)
}

private fun jsonBodyOf(vararg pairs: Pair<String, Any>): String =
    testingObjectMapper.writeValueAsString(mapOf(*pairs))

private fun jsonBodyOfNullable(vararg pairs: Pair<String, Any?>): String =
    testingObjectMapper.writeValueAsString(mapOf(*pairs))
