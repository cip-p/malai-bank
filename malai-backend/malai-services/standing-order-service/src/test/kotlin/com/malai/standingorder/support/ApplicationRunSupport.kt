package com.malai.standingorder.support

import com.malai.standingorder.StandingOrderServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runStandingOrderService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    paymentsBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        StandingOrderServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        StandingOrdersTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.payments.base-url=$paymentsBaseUrl",
            "--malai.cognito.standing-order-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        )
