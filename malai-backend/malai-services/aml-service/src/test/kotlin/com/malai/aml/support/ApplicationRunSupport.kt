package com.malai.aml.support

import com.malai.aml.AmlServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runAmlService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    paymentsBaseUrl: String,
    mdrBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(AmlServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--spring.datasource.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.payments.base-url=$paymentsBaseUrl",
            "--malai.mdr.base-url=$mdrBaseUrl",
            "--malai.cognito.aml-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        )
