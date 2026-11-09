package com.malai.directdebit.support

import com.malai.directdebit.DirectDebitServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runDirectDebitService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    stubBaseUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        DirectDebitServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        DirectDebitsTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.stub.base-url=$stubBaseUrl",
        )
