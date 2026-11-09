package com.malai.sqs

data class QueueDefinition(val baseName: String)

interface QueueCatalog {
    fun definitions(): List<QueueDefinition>
}

object QueueNaming {
    fun physicalName(prefix: String, baseName: String): String = "$prefix$baseName.fifo"
    fun deadLetterName(prefix: String, baseName: String): String = "$prefix$baseName-dlq.fifo"
}
