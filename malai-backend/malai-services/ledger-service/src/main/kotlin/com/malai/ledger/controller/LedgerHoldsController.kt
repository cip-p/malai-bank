package com.malai.ledger.controller

import com.malai.ledger.model.HoldResponse
import com.malai.ledger.model.PlaceHoldRequest
import com.malai.ledger.model.ReleaseHoldRequest
import com.malai.ledger.model.SettleHoldRequest
import com.malai.ledger.model.SettleHoldResponse
import com.malai.ledger.service.HoldManager
import jakarta.validation.Valid
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/internal/ledger/holds")
class LedgerHoldsController(private val holdManager: HoldManager) {

    @GetMapping("/{holdId}", produces = [APPLICATION_JSON_VALUE])
    fun get(@PathVariable holdId: UUID): HoldResponse = holdManager.get(holdId)

    @PostMapping(produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun place(@Valid @RequestBody request: PlaceHoldRequest): HoldResponse =
        holdManager.place(request)

    @PostMapping("/{holdId}/settle", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun settle(
        @PathVariable holdId: UUID,
        @Valid @RequestBody request: SettleHoldRequest,
    ): SettleHoldResponse = holdManager.settle(holdId, request)

    @PostMapping("/{holdId}/release", produces = [APPLICATION_JSON_VALUE], consumes = [APPLICATION_JSON_VALUE])
    fun release(
        @PathVariable holdId: UUID,
        @Valid @RequestBody request: ReleaseHoldRequest,
    ): HoldResponse = holdManager.release(holdId, request.resolvedAt!!)
}
