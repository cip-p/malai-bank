package com.malai.swiftgateway.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("acceptance-tests")
class SwiftGatewayAcceptanceTestConfiguration {

    @Bean
    fun capturedSwiftEvents() = CapturedSwiftEvents()

    @Bean
    fun swiftEventCaptureListener(events: CapturedSwiftEvents) = SwiftEventCaptureListener(events)

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = AnonymousCredentialsProvider.create()
}
