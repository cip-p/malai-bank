package com.malai.sandbox.support

import com.malai.sandbox.SandboxServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runSandboxService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    stubBaseUrl: String,
    accountBaseUrl: String,
    cardBaseUrl: String,
    directDebitBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        SandboxServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--database.url=$databaseUrl",
        "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
        "--malai.stub.base-url=$stubBaseUrl",
        "--malai.account.base-url=$accountBaseUrl",
        "--malai.card.base-url=$cardBaseUrl",
        "--malai.direct-debit.base-url=$directDebitBaseUrl",
        "--malai.cognito.sandbox-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
    )
