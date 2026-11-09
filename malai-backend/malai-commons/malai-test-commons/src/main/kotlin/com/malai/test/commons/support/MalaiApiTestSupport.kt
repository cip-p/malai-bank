package com.malai.test.commons.support


class MalaiApiTestContext {

    private val contexts: MutableMap<Class<*>, Any> = HashMap()

    private fun <T : Any> getContext(contextClass: Class<T>): T {
        return try {
            contexts.putIfAbsent(contextClass, contextClass.getDeclaredConstructor().newInstance())
            @Suppress("UNCHECKED_CAST")
            contexts[contextClass] as T
        } catch (e: Exception) {
            throw RuntimeException(e)
        }
    }

    companion object {
        private val threadLocal = ThreadLocal<MalaiApiTestContext>()
        fun <T : Any> context(contextClass: Class<T>): T {
            return get().getContext(contextClass)
        }

        private fun get(): MalaiApiTestContext {
            var malaiApiTestContext = threadLocal.get()
            if (malaiApiTestContext == null) {
                malaiApiTestContext = MalaiApiTestContext()
                threadLocal.set(malaiApiTestContext)
            }
            return malaiApiTestContext
        }
    }
}
