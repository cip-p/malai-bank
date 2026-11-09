package com.malai.fpsgateway.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("acceptance-tests")
class FpsGatewayAcceptanceTestConfiguration {

    @Bean
    fun capturedFpsEvents() = CapturedFpsEvents()

    @Bean
    fun fpsEventCaptureListener(events: CapturedFpsEvents) = FpsEventCaptureListener(events)

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = AnonymousCredentialsProvider.create()
}
