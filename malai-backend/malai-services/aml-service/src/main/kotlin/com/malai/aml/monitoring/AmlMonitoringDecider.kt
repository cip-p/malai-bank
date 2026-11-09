package com.malai.aml.monitoring

interface AmlMonitoringDecider {
    val modelId: String
    val modelVersion: String
    fun decide(window: AmlWindow, renderedPrompt: String): List<TypologyHit>
}

class AmlDeciderTimedOutException(message: String) : RuntimeException(message)
