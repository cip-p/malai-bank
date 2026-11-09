package com.malai.api

import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class ReadyController {
    @GetMapping("/ready")
    @PreAuthorize("permitAll()")
    fun ready() = Unit
}
