package com.malai.commons.tracking

import org.slf4j.LoggerFactory

open class ThreadLocalContext<T>(val name: String, val factory: () -> T) {
    private val threadLocal = ThreadLocal<T>()
    private val contextListeners = mutableSetOf<ThreadLocalContextListener<T>>()
    private val logger = LoggerFactory.getLogger(javaClass)

    var value: T
        get() = valueOrNull ?: throw IllegalStateException("$name has not been set in this context")
        set(value) {
            contextListeners.forEach {
                runCatching { it.onSet(value) }
                    .onFailure { throwable ->
                        logger.error(
                            "Exception thrown by a context listener while setting $name[$value]",
                            throwable
                        )
                    }
            }
            threadLocal.set(value)
        }

    val valueOrNull: T?
        get() = threadLocal.get()

    fun valueOrCreate(fn: () -> T = factory) = valueOrNull ?: fn().also { value = it }

    fun unset() {
        valueOrNull.also { value ->
            contextListeners.forEach {
                runCatching { it.onUnset(value) }
                    .onFailure { throwable ->
                        logger.error(
                            "Exception thrown by a context listener while unsetting $name[$value]",
                            throwable
                        )
                    }
            }
        }
        threadLocal.set(null)
    }

    fun addListener(listener: ThreadLocalContextListener<T>) = contextListeners.add(listener)
    fun removeListener(listener: ThreadLocalContextListener<T>) = contextListeners.remove(listener)
}

interface ThreadLocalContextListener<T> {
    fun onSet(holder: T)
    fun onUnset(holder: T?)
}
