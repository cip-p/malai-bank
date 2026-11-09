package com.malai.security

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
@RequestMapping("/test-security")
class TestSecurityController {

    @GetMapping(value = ["decode-user-id"], produces = [APPLICATION_JSON_VALUE])
    fun decodeUserId(@AuthenticationPrincipal user: MalaiUserForUserIdOnly): DecodedDataWithUserId {
        return DecodedDataWithUserId(user.userId)
    }

    @GetMapping(value = ["decode-user-id-and-account-holder-id"], produces = [APPLICATION_JSON_VALUE])
    fun decodeUserIdAndAccountHolderId(@AuthenticationPrincipal user: MalaiUserWithAccountHolderId): DecodedDataWithUserIdAndAccountHolderId {
        return DecodedDataWithUserIdAndAccountHolderId(user.userId, user.accountHolderId)
    }
}

data class DecodedDataWithUserId(
    val userId: UUID,
)

data class DecodedDataWithUserIdAndAccountHolderId(
    val userId: UUID,
    val accountHolderId: UUID,
)
