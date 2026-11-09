package com.malai.api

import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit

// Serves the hand-authored cross-repo OpenAPI contract at GET /openapi.yaml.
//
// Implemented as a controller (not a Spring MVC static resource handler)
// because `WebMvcConfigurer.addResourceHandlers().addResourceLocations(...)`
// requires the location to be a directory-style prefix: when the handler
// pattern fully matches the URL (no `**` wildcard) the path-within-pattern
// is empty, and Spring's `PathResourceResolver` resolves the location's
// parent directory instead of the file. Quick task 260513-cqs replaced
// the prior `StaticOpenApiConfig` with this controller after the dev
// deploy returned 500 (`NoResourceFoundException`).
//
// The route is listed in `api-gateway-malai-api/main.tf` `public_routes`
// (Phase 1 D-11), so the spec is reachable without a JWT.
// `@PreAuthorize("permitAll()")` mirrors `ReadyController` and documents
// the public-access decision at the application layer.
@RestController
class OpenApiSpecController {

    @GetMapping("/openapi.yaml", produces = ["application/yaml"])
    @PreAuthorize("permitAll()")
    fun spec(): ResponseEntity<Resource> {
        val resource = ClassPathResource(CLASSPATH_LOCATION)
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(60, TimeUnit.SECONDS).cachePublic())
            .body(resource)
    }

    companion object {
        // Matches `malai-api/src/main/resources/static/openapi.yaml` — the
        // hand-authored spec frozen in Plan 02-16 (BACK-09).
        const val CLASSPATH_LOCATION = "static/openapi.yaml"
    }
}
