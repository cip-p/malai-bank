package com.malai.security.cognito.tracking

import com.malai.commons.tracking.MalaiSourceContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.http.SdkHttpRequest

/**
 * Adds an [ExecutionInterceptor] that tags every outbound AWS SDK call (e.g. Cognito) with
 * an `X-Malai-Source` header. The value comes from [MalaiSourceContext] when set (so each
 * logical service bundled inside malai-api gets its own attribution) and falls back to
 * `spring.application.name`. Lets the
 * [com.malai.test.acceptance.kensa.interaction.WireMockInteractionCapturer] identify which
 * service made each call (since the AWS SDK, unlike our HTTP clients, doesn't propagate the
 * header itself).
 *
 * Activated by the `acceptance-tests` and `e2e-tests` Spring profiles.
 */
@Configuration
@Profile("acceptance-tests", "e2e-tests")
class MalaiSourceAwsSdkInterceptorConfig {

    @Bean
    fun malaiSourceAwsSdkInterceptor(
        @Value("\${spring.application.name:unknown}") applicationName: String,
    ): ExecutionInterceptor = object : ExecutionInterceptor {
        override fun modifyHttpRequest(
            context: Context.ModifyHttpRequest,
            executionAttributes: ExecutionAttributes
        ): SdkHttpRequest =
            context.httpRequest().toBuilder()
                .putHeader("X-Malai-Source", MalaiSourceContext.valueOrNull?.name ?: applicationName)
                .build()
    }
}
