package com.malai.accountholder.support

import com.malai.accountholder.AccountHolderServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runAccountHolderService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
): ConfigurableApplicationContext =
    SpringApplication(AccountHolderServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
        )
