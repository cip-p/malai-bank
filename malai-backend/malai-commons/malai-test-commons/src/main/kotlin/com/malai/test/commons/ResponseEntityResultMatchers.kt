package com.malai.test.commons

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import com.neovibrant.kacey.matchers.ApiJsonMatcher
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import com.neovibrant.kacey.matchers.Prop
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultMatcher

class ResponseEntityResultMatchers {

    private val objectMapper: ObjectMapper = ObjectMapper()

    companion object {
        fun responseEntity() = ResponseEntityResultMatchers()
    }

    fun containsProp(json: Prop): ResultMatcher {
        return ResultMatcher { result: MvcResult ->
            val httpResponse = result.response
            val responseEntity = when (val jsonResponse = httpResponse.contentAsString) {
                "" -> httpResponse.errorMessage?.let { mapOf("message" to it) } as? Prop
                else -> objectMapper.readValue<Prop>(jsonResponse)
            }

            responseEntity.shouldNotBeNull() should ApiJsonMatcher.containsProp(json)
        }
    }

    fun isEmpty(): ResultMatcher {
        return ResultMatcher { result: MvcResult ->
            assertThat(result.response.contentAsString, Matchers.emptyString())
        }
    }
}
