package com.malai.cardgateway.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("acceptance-tests")
class CardGatewayAcceptanceTestConfiguration {

    @Bean
    fun capturedCardEvents() = CapturedCardEvents()

    @Bean
    fun cardEventCaptureListener(events: CapturedCardEvents) = CardEventCaptureListener(events)

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = AnonymousCredentialsProvider.create()
}
