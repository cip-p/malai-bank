package com.malai.insights.support

import com.malai.insights.InsightsServiceApplication
import com.malai.test.acceptance.AcceptanceTestsConfiguration
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

fun runInsightsService(): ConfigurableApplicationContext =
    SpringApplication(
        InsightsServiceApplication::class.java,
        AcceptanceTestsConfiguration::class.java,
    ).run(
        "--spring.profiles.active=test,acceptance-tests",
        "--server.port=0",
        "--spring.autoconfigure.exclude=" +
            "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration," +
            "org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration",
    )
