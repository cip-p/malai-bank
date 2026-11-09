package com.malai.commons.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper

/**
 * Pure-function coverage for [BodyRedactor]. Filter-level integration is in
 * [RequestResponseLoggingFilterAcceptanceTest]; here we just nail the redaction
 * algorithm — denylist matching, recursion, immutability of the input.
 */
class BodyRedactorTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `redacts top-level sensitive fields`() {
        val input = mapper.readTree("""{"username":"a@b.com","password":"hunter2","keep":"yes"}""")

        val out = BodyRedactor.redact(input)

        assertThat(out.path("username").asString()).isEqualTo("*****")
        assertThat(out.path("password").asString()).isEqualTo("*****")
        assertThat(out.path("keep").asString()).isEqualTo("yes")
    }

    @Test
    fun `redacts response token fields`() {
        val input = mapper.readTree(
            """{"userId":"u-1","accessToken":"a","refreshToken":"r","idToken":"i","expiresIn":3600}"""
        )

        val out = BodyRedactor.redact(input)

        assertThat(out.path("userId").asString()).isEqualTo("u-1")
        assertThat(out.path("accessToken").asString()).isEqualTo("*****")
        assertThat(out.path("refreshToken").asString()).isEqualTo("*****")
        assertThat(out.path("idToken").asString()).isEqualTo("*****")
        assertThat(out.path("expiresIn").asInt()).isEqualTo(3600)
    }

    @Test
    fun `is case insensitive on field names`() {
        val input = mapper.readTree("""{"PassWord":"x","ACCESSTOKEN":"y","UserName":"z"}""")

        val out = BodyRedactor.redact(input)

        assertThat(out.path("PassWord").asString()).isEqualTo("*****")
        assertThat(out.path("ACCESSTOKEN").asString()).isEqualTo("*****")
        assertThat(out.path("UserName").asString()).isEqualTo("*****")
    }

    @Test
    fun `redacts nested objects at any depth`() {
        val input = mapper.readTree(
            """{"outer":{"inner":{"password":"deep","fine":"ok"}},"top":"keep"}"""
        )

        val out = BodyRedactor.redact(input)

        assertThat(out.path("outer").path("inner").path("password").asString()).isEqualTo("*****")
        assertThat(out.path("outer").path("inner").path("fine").asString()).isEqualTo("ok")
        assertThat(out.path("top").asString()).isEqualTo("keep")
    }

    @Test
    fun `redacts inside arrays`() {
        val input = mapper.readTree(
            """{"users":[{"username":"a@b.com","name":"Alice"},{"username":"c@d.com","name":"Carol"}]}"""
        )

        val out = BodyRedactor.redact(input)

        assertThat(out.path("users").get(0).path("username").asString()).isEqualTo("*****")
        assertThat(out.path("users").get(0).path("name").asString()).isEqualTo("Alice")
        assertThat(out.path("users").get(1).path("username").asString()).isEqualTo("*****")
        assertThat(out.path("users").get(1).path("name").asString()).isEqualTo("Carol")
    }

    @Test
    fun `leaves primitives, nulls and booleans unchanged when key is not sensitive`() {
        val input = mapper.readTree("""{"count":42,"flag":true,"missing":null,"name":"Bob"}""")

        val out = BodyRedactor.redact(input)

        assertThat(out.path("count").asInt()).isEqualTo(42)
        assertThat(out.path("flag").asBoolean()).isTrue
        assertThat(out.path("missing").isNull).isTrue
        assertThat(out.path("name").asString()).isEqualTo("Bob")
    }

    @Test
    fun `does not mutate the input tree`() {
        val raw = """{"password":"secret","name":"Bob"}"""
        val input = mapper.readTree(raw)

        BodyRedactor.redact(input)

        // Re-serialise the input and confirm it still matches the original.
        assertThat(mapper.writeValueAsString(input)).isEqualTo(raw)
    }
}
