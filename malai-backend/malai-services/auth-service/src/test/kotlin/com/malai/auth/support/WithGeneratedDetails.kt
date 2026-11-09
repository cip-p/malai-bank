package com.malai.auth.support

import com.malai.auth.service.GeneratedAddress
import com.malai.auth.service.GeneratedDetails
import com.malai.auth.service.GeneratedDetailsGenerator
import com.malai.test.acceptance.WithApplicationContext
import dev.kensa.Action
import dev.kensa.GivensContext
import java.time.LocalDate

interface WithGeneratedDetails : WithApplicationContext {

    fun stubbedGeneratedDetailsGenerator(): StubbedGeneratedDetailsGenerator =
        getBean(GeneratedDetailsGenerator::class.java) as StubbedGeneratedDetailsGenerator

    fun generatedDetailsAre(
        dateOfBirth: LocalDate,
        line1: String,
        line2: String?,
        city: String,
        postcode: String,
        country: String,
    ) = Action<GivensContext> {
        stubbedGeneratedDetailsGenerator().givenGeneratedDetailsAre(
            GeneratedDetails(
                dateOfBirth = dateOfBirth,
                address = GeneratedAddress(
                    line1 = line1,
                    line2 = line2,
                    city = city,
                    postcode = postcode,
                    country = country,
                ),
            )
        )
    }
}
