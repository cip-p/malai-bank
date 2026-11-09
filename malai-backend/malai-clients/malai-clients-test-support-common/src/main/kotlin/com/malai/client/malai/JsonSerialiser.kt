package com.malai.client.malai

import tools.jackson.databind.SerializationFeature.INDENT_OUTPUT
import tools.jackson.databind.json.JsonMapper

class JsonSerialiser {

    companion object {
        private val objectMapper = JsonMapper.builder()
            .enable(INDENT_OUTPUT)
            .build()

        fun <T> fromJson(json: String, clazz: Class<T>): T = objectMapper.readValue(json, clazz)
        fun <T> toJson(obj: T): String = objectMapper.writeValueAsString(obj)
    }
}
