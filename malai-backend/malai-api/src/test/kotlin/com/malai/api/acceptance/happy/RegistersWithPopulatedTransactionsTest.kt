package com.malai.api.acceptance.happy

import com.malai.accountholder.entity.AccountHolderEntity
import com.malai.accountholder.entity.AccountHolderType
import com.malai.accountholder.entity.IndividualEntity
import com.malai.accounts.entity.AccountEntity
import com.malai.accounts.entity.AccountIdentifierEntity
import com.malai.accounts.entity.AccountType
import com.malai.accounts.entity.IdentifierType
import com.malai.api.acceptance.MalaiApiAcceptanceTest
import com.malai.auth.entity.KycStatus
import com.malai.auth.entity.UserEntity
import com.malai.client.malai.auth.builders.request.VerifyCodeRequestBuilder.Companion.aDefaultVerifyCodeRequest
import com.malai.commons.banking.MalaiSortCode
import com.malai.commons.model.Currency
import com.malai.commons.model.OnboardingStatus
import com.malai.registrationseeder.data.ClearingAccountsSeed
import com.malai.registrationseeder.data.ForecastSeed
import com.malai.test.acceptance.kensa.CallableAction
import com.malai.test.acceptance.kensa.stub.HttpRequest.HttpRequestBuilder.Companion.post
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus.OK
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RegistersWithPopulatedTransactionsTest : MalaiApiAcceptanceTest() {

    @BeforeEach
    fun truncateAllFixtureTables() {
        cleanTables()
    }

    @Test
    fun verifyCodeAutoSeedsTheNewCustomersCurrentAccount() = malaiAcceptanceTest {
        given(theAuditChainIsAtGenesis())
        and(aRegisterPendingUserAndClearingsExist(someUserId(), someAccountHolderUid(), someAccountUid(), someEmail()))
        and(cognitoConfirmSignUpReturnsSession("some-verification-session"))
        and(cognitoVerificationSessionExchangeReturns(
            session = "some-verification-session",
            idToken = "some-id-token",
            accessToken = "some-access-token",
            refreshToken = "some-refresh-token",
            expiresIn = 2400,
        ))
        and(oauth2ServiceTokenEndpointReturnsToken())
        and(activateOnboardingAccountHolderForUserReturnsSuccess(someUserId()))
        and(activateOnboardingAccountsForUserReturnsSuccess(someUserId()))

        whenever(malaiApiReceives(
            post()
                .url("/api/v1/auth/verify-code")
                .body(aDefaultVerifyCodeRequest()
                    .withEmail(someEmail())
                    .withConfirmationCode("some-confirmation-code")
                ))
        )

        then(theLastResponseHas()) {
            statusCode shouldBe OK
        }

        and(theLedgerEntriesInTheDatabase(someAccountUid())) {
            this.size.shouldBeBetween(
                20 + ForecastSeed.COMPLETED_POSTING_COUNT,
                30 + ForecastSeed.COMPLETED_POSTING_COUNT,
            )
        }
    }

    private fun theAuditChainIsAtGenesis() = CallableAction {
        val jdbc = getBean(JdbcTemplate::class.java)
        jdbc.update("DELETE FROM audit_events")
        jdbc.update(
            """
            INSERT INTO chain_head (scope, last_hash, last_seq)
            VALUES ('global', E'\\x0000000000000000000000000000000000000000000000000000000000000000'::bytea, 0)
            ON CONFLICT (scope) DO UPDATE
                SET last_hash = excluded.last_hash, last_seq = excluded.last_seq
            """.trimIndent(),
        )
    }

    private fun aRegisterPendingUserAndClearingsExist(
        userId: UUID,
        accountHolderId: UUID,
        accountId: UUID,
        email: String,
    ) = CallableAction {
        val createdAt = Instant.parse("2026-05-01T00:00:00Z")

        // User exists in the DB but registrationConfirmed=false — the
        // RegistrationOrchestrator.completeRegistration flow will flip it to true
        // before invoking the post-registration hook chain.
        userRepository().save(
            UserEntity(
                id = userId,
                email = email,
                firstName = "Test",
                lastName = "User",
                dateOfBirth = LocalDate.of(1990, 1, 1),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = false,
                createdAt = createdAt,
                createdBy = "test",
            )
        )
        // Account holder + individual + account are pre-staged as ACTIVE
        // because the orchestrator's downstream activation HTTP calls are
        // WireMock-stubbed in this substrate; the seeder needs a real
        // ACTIVE CURRENT account to attach the seeded ledger entries to.
        accountHolderRepository().save(
            AccountHolderEntity(
                accountHolderId = accountHolderId,
                userId = userId,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = createdAt,
                status = OnboardingStatus.ACTIVE,
            )
        )
        individualRepository().save(
            IndividualEntity(
                individualId = UUID.randomUUID(),
                accountHolderId = accountHolderId,
                title = null,
                firstName = "Test",
                lastName = "User",
                dateOfBirth = LocalDate.of(1990, 1, 1),
                email = email,
                phone = "+447700900000",
                status = OnboardingStatus.ACTIVE,
            )
        )
        accountRepository().save(
            AccountEntity(
                accountId = accountId,
                accountType = AccountType.PRIMARY,
                defaultCategory = UUID.randomUUID(),
                currency = Currency.GBP,
                createdAt = createdAt,
                name = "Personal",
                userId = userId,
                accountHolderId = accountHolderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
        accountIdentifierRepository().save(
            AccountIdentifierEntity(
                identifierId = UUID.randomUUID(),
                accountId = accountId,
                identifierType = IdentifierType.SORT_CODE,
                bankIdentifier = MalaiSortCode.CANONICAL,
                accountIdentifier = "87654321",
            )
        )

        val systemUserId = ClearingAccountsSeed.SYSTEM_USER_ID
        val systemHolderId = ClearingAccountsSeed.SYSTEM_ACCOUNT_HOLDER_ID
        userRepository().save(
            UserEntity(
                id = systemUserId,
                email = ClearingAccountsSeed.SYSTEM_EMAIL,
                firstName = "System",
                lastName = "Bank",
                dateOfBirth = LocalDate.of(1970, 1, 1),
                kycStatus = KycStatus.VERIFIED,
                registrationConfirmed = true,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                createdBy = "test",
            )
        )
        accountHolderRepository().save(
            AccountHolderEntity(
                accountHolderId = systemHolderId,
                userId = systemUserId,
                accountHolderType = AccountHolderType.INDIVIDUAL,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                status = OnboardingStatus.ACTIVE,
            )
        )
        accountRepository().save(
            AccountEntity(
                accountId = ClearingAccountsSeed.SCHEME_SETTLEMENT,
                accountType = AccountType.SUSPENSE,
                defaultCategory = UUID.randomUUID(),
                currency = Currency.GBP,
                createdAt = Instant.parse("2025-01-01T00:00:00Z"),
                name = "Scheme Settlement",
                userId = systemUserId,
                accountHolderId = systemHolderId,
                status = OnboardingStatus.ACTIVE,
            )
        )
    }
}
