package com.malai.stub.directory

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StubPanRegistryTest {

    private val registry = StubPanRegistry()

    @Test
    fun startsEmpty() {
        assertThat(registry.all()).isEmpty()
    }

    @Test
    fun registersAndListsAPan() {
        registry.register("9904320000012341")

        assertThat(registry.all()).containsExactly("9904320000012341")
        assertThat(registry.contains("9904320000012341")).isTrue()
    }

    @Test
    fun deduplicatesRepeatedRegistrationsPreservingOrder() {
        registry.register("9904320000012341")
        registry.register("9904320000098765")
        registry.register("9904320000012341")

        assertThat(registry.all()).containsExactly("9904320000012341", "9904320000098765")
    }
}
