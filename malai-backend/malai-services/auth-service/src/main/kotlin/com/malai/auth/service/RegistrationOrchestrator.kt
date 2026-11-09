package com.malai.auth.service

import com.malai.auth.entity.KycStatus.VERIFIED
import com.malai.auth.model.RegisterRequest
import com.malai.client.malai.accountholder.onboarding.AccountHolderOnboardingClient
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAccountHolderRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingAddressRequest
import com.malai.client.malai.accountholder.onboarding.model.CreateOnboardingIndividualRequest
import com.malai.client.malai.accounts.onboarding.AccountsOnboardingClient
import com.malai.client.malai.accounts.onboarding.model.CreateOnboardingAccountRequest
import com.malai.security.MalaiRole
import com.malai.security.cognito.CognitoAdminClient
import com.malai.security.cognito.CognitoClient
import com.malai.security.cognito.model.CognitoPasswordlessRegisterRequest
import com.malai.security.cognito.model.CognitoVerificationTokens
import com.malai.security.cognito.model.CognitoVerifyRegistrationRequest
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class RegistrationOrchestrator(
    private val cognitoClient: CognitoClient,
    private val cognitoAdminClient: CognitoAdminClient,
    private val userService: UserService,
    private val authServiceTokenClient: AuthServiceTokenClient,
    private val accountHolderOnboardingClient: AccountHolderOnboardingClient,
    private val accountsOnboardingClient: AccountsOnboardingClient,
    private val generatedDetailsGenerator: GeneratedDetailsGenerator,
    private val postRegistrationHooks: List<PostRegistrationHook>,
    // Demo policy: Malai's registered users are bank evaluators, so the demo
    // platform grants every hat at sign-up while role enforcement at the API
    // stays real. A bank deploying this for real leaves the property off and
    // registration grants `customer` alone.
    @Value("\${malai.auth.registration.demo-all-roles:false}") private val demoAllRoles: Boolean,
) {

    private val log = LoggerFactory.getLogger(this.javaClass)

    fun register(request: RegisterRequest): UUID {
        val generatedDetails = generatedDetailsGenerator.generate()

        val cognitoResponse = cognitoClient.registerPasswordless(
            CognitoPasswordlessRegisterRequest(
                email = request.email!!,
                name = "${request.firstName} ${request.lastName}"
            )
        )

        // Without this, the JWT carries no `cognito:groups` claim, JwtDecoderFilter
        // resolves an empty role set, and every @PreAuthorize-gated endpoint 403s.
        cognitoAdminClient.addUserToGroup(request.email, MalaiRole.CUSTOMER.cognitoGroup)
        if (demoAllRoles) {
            cognitoAdminClient.addUserToGroup(request.email, MalaiRole.ADMIN.cognitoGroup)
            cognitoAdminClient.addUserToGroup(request.email, MalaiRole.MANAGER.cognitoGroup)
        }

        val userId = cognitoResponse.userId
        userService.createUser(
            userId = userId,
            email = request.email,
            firstName = request.firstName!!,
            lastName = request.lastName!!,
            dateOfBirth = generatedDetails.dateOfBirth,
            kycStatus = VERIFIED,
        )

        val token = authServiceTokenClient.getToken()
        val accountHolderResponse = accountHolderOnboardingClient.createAccountHolder(
            token,
            CreateOnboardingAccountHolderRequest(userId = userId),
        )
        val accountHolderId = accountHolderResponse.accountHolderId

        accountHolderOnboardingClient.createIndividual(
            token,
            accountHolderId,
            CreateOnboardingIndividualRequest(
                firstName = request.firstName,
                lastName = request.lastName,
                dateOfBirth = generatedDetails.dateOfBirth.toString(),
                email = request.email,
            ),
        )

        val address = generatedDetails.address
        accountHolderOnboardingClient.createAddress(
            token,
            accountHolderId,
            CreateOnboardingAddressRequest(
                line1 = address.line1,
                line2 = address.line2,
                city = address.city,
                postcode = address.postcode,
                country = address.country,
            ),
        )

        accountsOnboardingClient.createAccount(
            token,
            CreateOnboardingAccountRequest(
                userId = userId,
                accountHolderId = accountHolderId,
                currency = "GBP",
                name = "Personal",
            ),
        )

        log.info("register orchestrator complete, user id $userId")
        return userId
    }

    fun completeRegistration(email: String, confirmationCode: String): CognitoVerificationTokens {
        val tokens = cognitoClient.verifyRegistration(CognitoVerifyRegistrationRequest(email, confirmationCode))

        val user = userService.updateRegistrationConfirmed(email)
        val userId = user.id

        val token = authServiceTokenClient.getToken()
        accountHolderOnboardingClient.activateByUserId(token, userId)
        accountsOnboardingClient.activateAccountsForUser(token, userId)

        runPostRegistrationHooks(userId, email)

        log.info("verification completed registration, user id $userId")
        return tokens
    }

    private fun runPostRegistrationHooks(userId: UUID, email: String) {
        postRegistrationHooks.forEach { hook ->
            val startedAt = System.nanoTime()
            hook.afterConfirmRegister(userId, email)
            val durationMs = (System.nanoTime() - startedAt) / 1_000_000
            log.info(
                "registration.postHook",
                kv("userId", userId.toString()),
                kv("hookClass", hook.javaClass.simpleName),
                kv("durationMs", durationMs),
            )
        }
    }
}
