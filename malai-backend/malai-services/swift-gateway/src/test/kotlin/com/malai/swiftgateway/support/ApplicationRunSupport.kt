package com.malai.swiftgateway.support

import com.malai.swiftgateway.SwiftGatewayApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "g${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runSwiftGatewayService(swiftSchemeBaseUrl: String): ConfigurableApplicationContext =
    SpringApplication(
        SwiftGatewayApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        SwiftGatewayAcceptanceTestConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "--malai.swift-scheme.base-url=$swiftSchemeBaseUrl",
        "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
    )
