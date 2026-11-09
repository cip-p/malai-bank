package com.malai.exec.support

import com.malai.exec.ExecServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runExecService(
    cognitoJwtIssuer: String,
    accountHolderBaseUrl: String,
    paymentsBaseUrl: String,
    cardBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(ExecServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.account-holder.base-url=$accountHolderBaseUrl",
            "--malai.payments.base-url=$paymentsBaseUrl",
            "--malai.card.base-url=$cardBaseUrl",
            "--malai.cognito.exec-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        )
