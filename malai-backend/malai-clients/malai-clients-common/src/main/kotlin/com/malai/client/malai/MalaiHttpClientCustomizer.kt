package com.malai.client.malai

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder

fun interface MalaiHttpClientCustomizer {
    fun customize(builder: HttpClientBuilder)
}
