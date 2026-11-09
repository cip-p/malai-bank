package com.malai.copilot.support

import com.malai.copilot.CopilotServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runCopilotService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    paymentsBaseUrl: String,
    ledgerBaseUrl: String,
    mdrBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(CopilotServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--spring.datasource.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.payments.base-url=$paymentsBaseUrl",
            "--malai.ledger.base-url=$ledgerBaseUrl",
            "--malai.mdr.base-url=$mdrBaseUrl",
            "--malai.cognito.copilot-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        )
