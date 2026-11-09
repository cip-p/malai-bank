package com.malai.fpsgateway.support

import com.malai.fpsgateway.FpsGatewayApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "g${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runFpsGatewayService(
    fpsSchemeBaseUrl: String,
    queueNamePrefix: String = QUEUE_NAME_PREFIX,
): ConfigurableApplicationContext =
    SpringApplication(
        FpsGatewayApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        FpsGatewayAcceptanceTestConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "--malai.fps-scheme.base-url=$fpsSchemeBaseUrl",
        "--malai.sqs.queue-name-prefix=$queueNamePrefix",
    )
