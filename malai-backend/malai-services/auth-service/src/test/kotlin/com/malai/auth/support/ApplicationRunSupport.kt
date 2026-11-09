package com.malai.auth.support

import com.malai.auth.AuthServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runAuthService(
    databaseUrl: String,
    cognitoBaseUrl: String,
    cognitoJwtIssuer: String,
    accountHolderBaseUrl: String,
    accountsBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        AuthServiceApplication::class.java,
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
            "--malai.cognito.auth-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.cognito.auth-service.client-id=test-service-client-id",
            "--malai.cognito.auth-service.client-secret=test-service-client-secret",
            "--malai.cognito.auth-service.scope=malai-internal/onboarding.write",
            "--malai.aws.cognito.user-pool-id=test-user-pool-id",
        )
