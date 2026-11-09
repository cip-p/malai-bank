package com.malai.payments.screening

interface ScreeningDecider {

    val modelId: String

    val modelVersion: String

    fun decide(subject: ScreeningSubject, renderedPrompt: String): ScreeningVerdict
}

class ScreeningTimedOutException(message: String) : RuntimeException(message)
