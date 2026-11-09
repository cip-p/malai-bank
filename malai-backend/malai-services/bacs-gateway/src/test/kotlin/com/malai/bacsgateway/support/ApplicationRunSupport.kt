package com.malai.bacsgateway.support

import com.malai.bacsgateway.BacsGatewayApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "g${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runBacsGatewayService(bacsSchemeBaseUrl: String): ConfigurableApplicationContext =
    SpringApplication(
        BacsGatewayApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        BacsGatewayAcceptanceTestConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "--malai.bacs-scheme.base-url=$bacsSchemeBaseUrl",
        "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
    )
