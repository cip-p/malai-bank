package com.malai.payments.support

import com.malai.payments.PaymentsServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.*

private val QUEUE_NAME_PREFIX = "p${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runPaymentsService(
    databaseUrl: String,
    cognitoJwtIssuer: String,
    ledgerBaseUrl: String,
    accountBaseUrl: String,
    mdrBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        PaymentsServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        PaymentsAcceptanceTestConfiguration::class.java,
    )
        .run(
            "--spring.profiles.active=test,acceptance-tests",
            "--server.port=0",
            "--database.url=$databaseUrl",
            "--malai.cognito.jwtToken.issuer=$cognitoJwtIssuer",
            "--malai.ledger.base-url=$ledgerBaseUrl",
            "--malai.account.base-url=$accountBaseUrl",
            "--malai.mdr.base-url=$mdrBaseUrl",
            "--malai.cognito.payments-service.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
            "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
        )
