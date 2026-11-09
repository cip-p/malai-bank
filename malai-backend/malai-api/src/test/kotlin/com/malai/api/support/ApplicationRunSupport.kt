package com.malai.api.support

import com.malai.api.MalaiApiApplication
import com.malai.auth.support.AuthServiceTestConfiguration
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.*

private val QUEUE_NAME_PREFIX = "a${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runMalaiApi(
    databaseUrl: String,
    cognitoBaseUrl: String,
    cognitoJwtIssuer: String,
    accountHolderBaseUrl: String,
    accountsBaseUrl: String,
    ledgerBaseUrl: String,
    cardBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        MalaiApiApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        AuthServiceTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.endpointOverride=$cognitoBaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.account-holder.base-url=$accountHolderBaseUrl",
            "--malai.account.base-url=$accountsBaseUrl",
            "--malai.ledger.base-url=$ledgerBaseUrl",
            "--malai.card.base-url=$cardBaseUrl",
            "--malai.cognito.auth-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.cognito.auth-service.client-id=test-service-client-id",
            "--malai.cognito.auth-service.client-secret=test-service-client-secret",
            "--malai.cognito.auth-service.scope=malai-internal/onboarding.write",
            "--malai.cognito.account-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.cognito.account-service.client-id=test-service-client-id",
            "--malai.cognito.account-service.client-secret=test-service-client-secret",
            "--malai.cognito.account-service.scope=malai-internal/onboarding.write",
            "--malai.cognito.payments-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.cognito.payments-service.client-id=test-service-client-id",
            "--malai.cognito.payments-service.client-secret=test-service-client-secret",
            "--malai.cognito.payments-service.scope=malai-internal/onboarding.write",
            "--malai.cognito.standing-order-service.client-id=test-service-client-id",
            "--malai.cognito.standing-order-service.client-secret=test-service-client-secret",
            "--malai.cognito.standing-order-service.scope=malai-internal/onboarding.write",
            "--malai.cognito.standing-order-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.aws.cognito.user-pool-id=test-user-pool-id",
            "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
        )
