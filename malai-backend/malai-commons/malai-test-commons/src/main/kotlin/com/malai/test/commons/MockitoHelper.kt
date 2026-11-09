package com.malai.test.commons

import org.mockito.Mockito


object MockitoHelper {
    fun <T> anyObject(): T {
        Mockito.any<T>()
        return uninitialized()
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> uninitialized(): T = null as T

    inline fun <reified T : Any> mockGenericClass() = Mockito.mock(T::class.java)

}
