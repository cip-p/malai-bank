package com.malai.client.malai.tracking

import com.malai.client.malai.MalaiHttpClientCustomizer
import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Adds a [MalaiHttpClientCustomizer] that propagates [TestId.HEADER] on every outbound
 * Malai HTTP client call whenever a [TestIdContext] value is present on the current thread.
 *
 * Activated by the `acceptance-tests` Spring profile.
 */
@Configuration
@Profile("acceptance-tests", "e2e-tests")
class TestIdHttpClientConfig {

    @Bean
    fun testIdMalaiHttpClientCustomizer(): MalaiHttpClientCustomizer =
        MalaiHttpClientCustomizer { builder ->
            builder.addRequestInterceptorFirst { request, _, _ ->
                TestIdContext.valueOrNull?.let { request.addHeader(TestId.HEADER, it.value) }
            }
        }
}
