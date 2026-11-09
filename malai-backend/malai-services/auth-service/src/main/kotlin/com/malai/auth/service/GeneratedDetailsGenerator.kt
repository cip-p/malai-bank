package com.malai.auth.service

import com.malai.commons.TimeService
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.util.Random

data class GeneratedAddress(
    val line1: String,
    val line2: String?,
    val city: String,
    val postcode: String,
    val country: String
)

data class GeneratedDetails(
    val dateOfBirth: LocalDate,
    val address: GeneratedAddress
)

open class GeneratedDetailsGenerator(
    private val timeService: TimeService,
    private val random: Random,
) {

    open fun generate(): GeneratedDetails {
        val address = CURATED_ADDRESSES[random.nextInt(CURATED_ADDRESSES.size)]
        val today = LocalDate.ofInstant(timeService.now(), UTC)
        val ageYears = MIN_AGE_YEARS + random.nextInt(MAX_AGE_YEARS - MIN_AGE_YEARS + 1)
        val dateOfBirth = today
            .minusYears(ageYears.toLong())
            .minusDays(random.nextInt(364).toLong())
        return GeneratedDetails(dateOfBirth, address)
    }

    companion object {
        private const val MIN_AGE_YEARS = 25
        private const val MAX_AGE_YEARS = 65

        private val CURATED_ADDRESSES = listOf(
            GeneratedAddress("14 Rosebery Avenue", null, "London", "EC1R 4TD", "GB"),
            GeneratedAddress("87 Whiteladies Road", null, "Bristol", "BS8 2NT", "GB"),
            GeneratedAddress("3 Bold Street", null, "Liverpool", "L1 4DJ", "GB"),
            GeneratedAddress("41 Deansgate", null, "Manchester", "M3 2AY", "GB"),
            GeneratedAddress("12 George Street", null, "Edinburgh", "EH2 2PF", "GB"),
            GeneratedAddress("5 Mill Hill", null, "Leeds", "LS1 5DQ", "GB"),
            GeneratedAddress("28 Wake Green Road", "Flat 2", "Birmingham", "B13 9PP", "GB"),
            GeneratedAddress("9 Windsor Place", null, "Cardiff", "CF10 3BX", "GB"),
        )
    }
}
