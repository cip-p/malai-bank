package com.malai.seeder

import com.malai.security.cognito.CognitoConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import java.net.URI

// Seeder-local Spring config. Component scan stays bounded to `com.malai.seeder`
// so Picocli @Command + the seeder's own @Component beans are discovered without
// leaking onto the umbrella `com.malai` package (see Plan 02-11 anti-pattern).
//
// Provides one Cognito admin client bean. The region comes from the same
// `malai.cognito.*` properties the runtime binds (AWS_REGION-backed), and the
// optional endpoint override points the Admin* calls at the local Cognito stub
// when demo-reset-local.sh runs offline.
@Configuration
@ComponentScan(basePackages = ["com.malai.seeder"])
class SeederConfiguration {

    @Bean(destroyMethod = "close")
    fun seederCognitoIdentityProviderClient(
        cognitoProperties: CognitoConfigurationProperties,
    ): CognitoIdentityProviderClient {
        val builder = CognitoIdentityProviderClient.builder()
            .region(Region.of(cognitoProperties.region))
        cognitoProperties.endpointOverride?.let { builder.endpointOverride(URI.create(it)) }
        return builder.build()
    }
}
