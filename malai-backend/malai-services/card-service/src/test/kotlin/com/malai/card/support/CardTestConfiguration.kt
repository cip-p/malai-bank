package com.malai.card.support

import com.malai.card.pan.PanGenerator
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider

@Configuration
@Profile("test", "acceptance-tests")
class CardTestConfiguration {

    @Bean
    @Primary
    fun testPanGenerator(): PanGenerator = TestPanGenerator()

    @Bean
    fun awsCredentialsProvider(): AwsCredentialsProvider = AnonymousCredentialsProvider.create()
}
