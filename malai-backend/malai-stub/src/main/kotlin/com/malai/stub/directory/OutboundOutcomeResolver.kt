package com.malai.stub.directory

import org.springframework.stereotype.Component

@Component
class OutboundOutcomeResolver(private val directory: StubFictionalDirectory) {

    fun resolve(sortCode: String, accountNumber: String): OutboundOutcome =
        directory.find(sortCode, accountNumber)?.outboundOutcome ?: OutboundOutcome.SETTLES
}
