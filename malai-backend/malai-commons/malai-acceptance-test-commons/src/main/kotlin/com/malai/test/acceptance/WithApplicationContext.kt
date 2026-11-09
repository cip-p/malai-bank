package com.malai.test.acceptance

import org.springframework.context.ApplicationContext

/**
 * Mix this into any `With*` interface that needs to resolve beans from the running service's
 * Spring context. Uses the context captured by [MalaiAcceptanceTestContext.boot].
 */
interface WithApplicationContext {

    fun applicationContext(): ApplicationContext = MalaiAcceptanceTestContext.applicationContext

    fun <T : Any> getBean(type: Class<T>): T = applicationContext().getBean(type)
}
