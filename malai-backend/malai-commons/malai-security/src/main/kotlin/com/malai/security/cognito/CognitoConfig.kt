package com.malai.security.cognito

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient
import java.net.URI

@Configuration
class CognitoConfig(
    private val cognitoProperties: CognitoConfigurationProperties,
) {

    @Bean
    fun identityProviderClient(
        executionInterceptors: List<ExecutionInterceptor>
    ): CognitoIdentityProviderClient {
        val builder = CognitoIdentityProviderClient.builder()
            .region(Region.of(cognitoProperties.region))
            .credentialsProvider(AnonymousCredentialsProvider.create())

        cognitoProperties.endpointOverride?.let {
            builder.endpointOverride(URI.create(it))
        }

        if (executionInterceptors.isNotEmpty()) {
            builder.overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apply { executionInterceptors.forEach { addExecutionInterceptor(it) } }
                    .build()
            )
        }

        return builder.build()
    }

    // Separate client for Cognito Admin* APIs. The user-facing flows (SignUp,
    // InitiateAuth, ConfirmSignUp, ...) authenticate the request via SECRET_HASH
    // and run with AnonymousCredentialsProvider, but Admin* operations are
    // IAM-signed against the ECS task role. Using the same anonymous bean for
    // both would silently 401 on every Admin* call in dev/prod (Wiremock in
    // acceptance tests doesn't enforce signing, hiding the regression).
    @Bean(destroyMethod = "close")
    fun adminIdentityProviderClient(
        executionInterceptors: List<ExecutionInterceptor>
    ): CognitoIdentityProviderClient {
        val builder = CognitoIdentityProviderClient.builder()
            .region(Region.of(cognitoProperties.region))
            // default credentials chain — ECS task role on Fargate, ambient creds locally

        cognitoProperties.endpointOverride?.let {
            builder.endpointOverride(URI.create(it))
        }

        if (executionInterceptors.isNotEmpty()) {
            builder.overrideConfiguration(
                ClientOverrideConfiguration.builder()
                    .apply { executionInterceptors.forEach { addExecutionInterceptor(it) } }
                    .build()
            )
        }

        return builder.build()
    }
}
