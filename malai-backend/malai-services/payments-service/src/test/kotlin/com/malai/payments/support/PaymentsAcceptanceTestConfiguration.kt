package com.malai.payments.support

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("acceptance-tests")
class PaymentsAcceptanceTestConfiguration {

    @Bean
    fun fpsCommandCaptureListener() = FpsCommandCaptureListener()

    @Bean
    fun fpsReturnCommandCaptureListener() = FpsReturnCommandCaptureListener()

    @Bean
    fun bacsCollectionReturnCommandCaptureListener() = BacsCollectionReturnCommandCaptureListener()

    @Bean
    fun swiftCommandCaptureListener() = SwiftCommandCaptureListener()

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider {
        return AnonymousCredentialsProvider.create()
    }
}
