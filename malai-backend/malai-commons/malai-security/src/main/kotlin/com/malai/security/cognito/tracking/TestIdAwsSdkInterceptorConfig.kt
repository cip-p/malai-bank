package com.malai.security.cognito.tracking

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.core.interceptor.Context
import software.amazon.awssdk.core.interceptor.ExecutionAttributes
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor
import software.amazon.awssdk.http.SdkHttpRequest

/**
 * Adds an [ExecutionInterceptor] that propagates [TestId.HEADER] on every outbound AWS SDK
 * call (e.g. Cognito) whenever a [TestIdContext] value is present on the current thread.
 *
 * Activated by the `acceptance-tests` Spring profile.
 */
@Configuration
@Profile("acceptance-tests", "e2e-tests")
class TestIdAwsSdkInterceptorConfig {

    @Bean
    fun testIdAwsSdkInterceptor(): ExecutionInterceptor = object : ExecutionInterceptor {
        override fun modifyHttpRequest(
            context: Context.ModifyHttpRequest,
            executionAttributes: ExecutionAttributes
        ): SdkHttpRequest {
            val testId = TestIdContext.valueOrNull ?: return context.httpRequest()
            return context.httpRequest().toBuilder()
                .putHeader(TestId.HEADER, testId.value)
                .build()
        }
    }
}
