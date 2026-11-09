package com.malai.client.malai

import com.malai.commons.MalaiCommonsConfiguration
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ComponentScan(basePackages = ["com.malai.client.malai"])
@Import(MalaiCommonsConfiguration::class)
class MalaiClientConfiguration {

    @Bean
    fun malaiHttpClientBuilder(customizers: List<MalaiHttpClientCustomizer>): HttpClientBuilder {
        val builder = HttpClientBuilder.create()

        customizers.forEach { it.customize(builder) }

        return builder
    }

    @Bean
    fun malaiHttpClient(malaiHttpClientBuilder: HttpClientBuilder): CloseableHttpClient =
        malaiHttpClientBuilder.build()
}
