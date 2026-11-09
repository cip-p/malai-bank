package com.malai.client.malai.tracking

import com.malai.client.malai.MalaiHttpClientCustomizer
import com.malai.commons.tracking.MalaiSourceContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Adds a [MalaiHttpClientCustomizer] that stamps `X-Malai-Source` on every outbound Malai HTTP
 * call. The value comes from [MalaiSourceContext] when set (so each logical service bundled
 * inside the malai-api deployment unit gets its own attribution) and falls back to
 * `spring.application.name`. Lets receivers identify which logical service made the call regardless
 * of deployment topology.
 *
 * Always-on — production logs and traces want this too, not just tests. Mirrors
 * [com.malai.commons.tracking.MalaiSourceServletFilterConfig] on the inbound side.
 */
@Configuration
class MalaiSourceHttpClientConfig(
    @Value("\${spring.application.name:unknown}") private val applicationName: String,
) {

    @Bean
    fun malaiSourceMalaiHttpClientCustomizer(): MalaiHttpClientCustomizer =
        MalaiHttpClientCustomizer { builder ->
            builder.addRequestInterceptorFirst { request, _, _ ->
                val source = MalaiSourceContext.valueOrNull?.name ?: applicationName
                request.addHeader(MALAI_SOURCE_HEADER, source)
            }
        }

    companion object {
        const val MALAI_SOURCE_HEADER: String = "X-Malai-Source"
    }
}
