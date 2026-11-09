package com.malai.aml.sar

import org.springframework.stereotype.Component

@Component
class SarGroundingValidator {

    fun isGrounded(statements: List<SarStatement>, timeline: SarTimeline): Boolean =
        statements.isNotEmpty() && statements.all { statement ->
            statement.sources.isNotEmpty() && statement.sources.all(timeline::admits)
        }
}
