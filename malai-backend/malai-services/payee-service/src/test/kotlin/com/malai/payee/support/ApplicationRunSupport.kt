package com.malai.payee.support

import com.malai.payee.PayeeServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runPayeeService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
): ConfigurableApplicationContext =
    SpringApplication(
        PayeeServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        PayeesTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
        )
