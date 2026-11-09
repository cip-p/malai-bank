package com.malai.sqs.tracking

import com.malai.commons.tracking.TestId
import com.malai.commons.tracking.TestIdContext
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.messaging.Message

@Configuration
@Profile("acceptance-tests", "e2e-tests")
class TestIdSqsInterceptorConfig {

    @Bean
    fun testIdSqsInterceptor(): MessageInterceptor<Any> = object : MessageInterceptor<Any> {
        override fun intercept(message: Message<Any>): Message<Any> {
            val testId = message.headers[TestId.HEADER]?.toString()
            if (testId != null) TestIdContext.value = TestId(testId)
            return message
        }

        override fun afterProcessing(message: Message<Any>, t: Throwable?) {
            TestIdContext.unset()
        }
    }
}
