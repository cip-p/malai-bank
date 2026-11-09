package com.malai.stub.support

import com.malai.stub.MalaiStubApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import java.util.UUID

private val QUEUE_NAME_PREFIX = "s${UUID.randomUUID().toString().replace("-", "").take(8)}-"

fun runStubService(
    fpsGatewayBaseUrl: String,
    swiftGatewayBaseUrl: String,
    bacsGatewayBaseUrl: String,
    cardGatewayBaseUrl: String,
): ConfigurableApplicationContext =
    SpringApplication(
        MalaiStubApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
        MalaiStubAcceptanceTestConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
        "--malai.fps-gateway.base-url=$fpsGatewayBaseUrl",
        "--malai.swift-gateway.base-url=$swiftGatewayBaseUrl",
        "--malai.bacs-gateway.base-url=$bacsGatewayBaseUrl",
        "--malai.card-gateway.base-url=$cardGatewayBaseUrl",
        "--malai.stub.swift.settle-delay-millis=0",
        "--malai.stub.card.present-delay-millis=2000",
        "--malai.sqs.queue-name-prefix=$QUEUE_NAME_PREFIX",
    )
