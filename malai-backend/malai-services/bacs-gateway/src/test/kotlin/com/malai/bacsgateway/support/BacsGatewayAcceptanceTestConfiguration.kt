package com.malai.bacsgateway.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("acceptance-tests")
class BacsGatewayAcceptanceTestConfiguration {

    @Bean
    fun capturedBacsEvents() = CapturedBacsEvents()

    @Bean
    fun bacsEventCaptureListener(events: CapturedBacsEvents) = BacsEventCaptureListener(events)

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = AnonymousCredentialsProvider.create()
}
