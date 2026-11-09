package com.malai.sqs

import tools.jackson.databind.json.JsonMapper
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory
import io.awspring.cloud.sqs.listener.SqsHeaders
import io.awspring.cloud.sqs.listener.Visibility
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode
import io.awspring.cloud.sqs.listener.errorhandler.ErrorHandler
import io.awspring.cloud.sqs.listener.interceptor.MessageInterceptor
import io.awspring.cloud.sqs.operations.SqsTemplate
import io.awspring.cloud.sqs.support.converter.SqsMessagingMessageConverter
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.Message
import software.amazon.awssdk.services.sqs.SqsAsyncClient

@ConfigurationProperties(prefix = "malai.sqs")
data class MalaiSqsMessagingProperties(
    val queueNamePrefix: String = "",
    val redeliveryBackoffSeconds: Int = 5,
)

@Configuration
@EnableConfigurationProperties(MalaiSqsMessagingProperties::class)
@ComponentScan(basePackages = ["com.malai.sqs"])
class MalaiSqsMessagingConfiguration {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun sqsTemplate(sqsAsyncClient: SqsAsyncClient, jsonMapper: JsonMapper): SqsTemplate =
        SqsTemplate.builder()
            .sqsAsyncClient(sqsAsyncClient)
            .messageConverter(messageConverter(jsonMapper))
            .build()

    @Bean
    fun defaultSqsListenerContainerFactory(
        sqsAsyncClient: SqsAsyncClient,
        jsonMapper: JsonMapper,
        props: MalaiSqsMessagingProperties,
        messageInterceptors: List<MessageInterceptor<Any>>,
    ): SqsMessageListenerContainerFactory<Any> {
        val converter = messageConverter(jsonMapper)
        val builder = SqsMessageListenerContainerFactory.builder<Any>()
            .sqsAsyncClient(sqsAsyncClient)
            .configure { options ->
                options
                    .acknowledgementMode(AcknowledgementMode.ON_SUCCESS)
                    .messageConverter(converter)
            }
            .errorHandler(visibilityExtendingErrorHandler(props.redeliveryBackoffSeconds))
        messageInterceptors.forEach(builder::messageInterceptor)
        return builder.build()
    }

    private fun messageConverter(jsonMapper: JsonMapper) =
        SqsMessagingMessageConverter(jsonMapper).apply {
            doNotSendPayloadTypeHeader()
        }

    private fun visibilityExtendingErrorHandler(backoffSeconds: Int): ErrorHandler<Any> =
        object : ErrorHandler<Any> {
            override fun handle(message: Message<Any>, t: Throwable) {
                message.headers.get(SqsHeaders.SQS_VISIBILITY_TIMEOUT_HEADER, Visibility::class.java)
                    ?.changeTo(backoffSeconds)
                log.warn(
                    "sqs listener failed; extended visibility by {}s before redelivery: {}",
                    backoffSeconds, t.message,
                )
                throw t
            }
        }
}
