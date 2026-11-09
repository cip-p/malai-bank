package com.malai.stub.cop

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cop/lookup")
class StubCopController(
    private val service: StubCopVerdictService,
) {

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun lookup(@RequestBody body: StubCopLookupRequest): StubCopLookupResponse =
        service.lookup(body)
}
