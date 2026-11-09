package com.malai.mdr.support

import com.malai.mdr.MdrServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runMdrService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
): ConfigurableApplicationContext =
    SpringApplication(MdrServiceApplication::class.java, AcceptanceTestsConfiguration::class.java)
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
        )
