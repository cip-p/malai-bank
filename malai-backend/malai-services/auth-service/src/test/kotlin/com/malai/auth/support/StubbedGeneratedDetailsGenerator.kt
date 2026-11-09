package com.malai.auth.support

import com.malai.auth.service.GeneratedDetails
import com.malai.auth.service.GeneratedDetailsGenerator
import com.malai.commons.TimeService
import java.security.SecureRandom

class StubbedGeneratedDetailsGenerator(timeService: TimeService) :
    GeneratedDetailsGenerator(timeService, SecureRandom()) {

    private var stubbedDetails: GeneratedDetails? = null

    override fun generate(): GeneratedDetails = stubbedDetails ?: super.generate()

    fun givenGeneratedDetailsAre(details: GeneratedDetails) {
        stubbedDetails = details
    }

    fun reset() {
        stubbedDetails = null
    }
}
