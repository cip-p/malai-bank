package com.malai.registrationseeder.controller

import com.malai.auth.repository.UserRepository
import com.malai.commons.exception.MalaiCode.USER_NOT_FOUND
import com.malai.commons.exception.UserNotFoundException
import com.malai.registrationseeder.RegistrationSeederService
import com.malai.registrationseeder.SeedResult
import com.malai.security.MalaiUserForUserIdOnly
import net.logstash.logback.argument.StructuredArguments.kv
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/seed-demo-data")
class SeedDemoDataController(
    private val userRepository: UserRepository,
    private val registrationSeederService: RegistrationSeederService,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping(value = [""])
    @PreAuthorize("hasRole('CUSTOMER')")
    fun seedDemoData(@AuthenticationPrincipal user: MalaiUserForUserIdOnly): ResponseEntity<Void> {
        val startedAt = System.nanoTime()

        val userEntity = userRepository.findById(user.userId)
            .orElseThrow { UserNotFoundException(USER_NOT_FOUND) }

        val result = registrationSeederService.seedFor(userEntity.email)
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000

        return when (result) {
            is SeedResult.Seeded -> {
                log.info(
                    "seedDemoData.endpoint",
                    kv("userId", user.userId.toString()),
                    kv("outcome", "seeded"),
                    kv("durationMs", durationMs),
                )
                ResponseEntity.ok().build()
            }
            is SeedResult.Noop -> {
                log.info(
                    "seedDemoData.endpoint",
                    kv("userId", user.userId.toString()),
                    kv("outcome", "noop"),
                    kv("durationMs", durationMs),
                )
                ResponseEntity.ok().build()
            }
            is SeedResult.NoActiveCurrentAccount -> {
                log.warn(
                    "seedDemoData.endpoint",
                    kv("userId", user.userId.toString()),
                    kv("outcome", "no_active_current_account"),
                    kv("durationMs", durationMs),
                )
                throw IllegalStateException(
                    "Cannot seed demo data: user ${user.userId} has no ACTIVE PRIMARY account"
                )
            }
            is SeedResult.UserNotFound -> {
                log.warn(
                    "seedDemoData.endpoint",
                    kv("userId", user.userId.toString()),
                    kv("outcome", "user_not_found_after_lookup"),
                    kv("durationMs", durationMs),
                )
                throw IllegalStateException(
                    "Seeder reported UserNotFound after findById succeeded for user ${user.userId}"
                )
            }
        }
    }
}
