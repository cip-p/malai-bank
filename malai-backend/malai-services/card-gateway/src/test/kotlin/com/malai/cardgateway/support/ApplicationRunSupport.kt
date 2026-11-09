package com.malai.cardgateway.support

import com.malai.cardgateway.CardGatewayApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "g${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runCardGatewayService(
    cardBaseUrl: String,
    cognitoServiceTokenEndpointUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        CardGatewayApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        CardGatewayAcceptanceTestConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "--malai.card.base-url=$cardBaseUrl",
        "--malai.cognito.card-gateway.token-endpoint-url=$cognitoServiceTokenEndpointUrl",
        "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
    )
