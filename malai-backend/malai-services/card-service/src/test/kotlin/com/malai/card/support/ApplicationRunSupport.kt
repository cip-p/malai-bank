package com.malai.card.support

import com.malai.card.CardServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "c${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runCardService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    ledgerBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        CardServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        CardTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.ledger.base-url=$ledgerBaseUrl",
            "--malai.cognito.card-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
        )
