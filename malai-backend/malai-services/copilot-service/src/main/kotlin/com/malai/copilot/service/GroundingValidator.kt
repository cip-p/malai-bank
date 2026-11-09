package com.malai.copilot.service

import com.malai.copilot.model.Timeline
import org.springframework.stereotype.Component

@Component
class GroundingValidator {

    fun isGrounded(claims: List<Claim>, timeline: Timeline): Boolean =
        claims.isNotEmpty() && claims.all { claim ->
            claim.sources.isNotEmpty() && claim.sources.all(timeline::admits)
        }
}
