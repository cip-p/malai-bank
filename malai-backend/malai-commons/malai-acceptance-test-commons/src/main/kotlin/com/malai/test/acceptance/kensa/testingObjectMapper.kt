package com.malai.test.acceptance.kensa

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import tools.jackson.databind.SerializationFeature
import kotlin.reflect.KClass
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.jacksonMapperBuilder

val testingObjectMapper: JsonMapper = jacksonMapperBuilder()
    .enable(SerializationFeature.INDENT_OUTPUT)
    .changeDefaultPropertyInclusion { JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS) }
    .build()

fun <T: Any> String.toObject(type: KClass<T>): T = testingObjectMapper.readValue(this, type.java)
