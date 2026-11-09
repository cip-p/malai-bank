package com.malai.commons.config

import org.springframework.http.MediaType.TEXT_PLAIN_VALUE
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class VersionController(private val applicationVersionHolder: ApplicationVersionHolder) {

    @GetMapping("/version", produces = [TEXT_PLAIN_VALUE])
    @PreAuthorize("permitAll()")
    fun version() = applicationVersionHolder.version
}
