package com.malai.accountholder.exception

import com.malai.commons.exception.MalaiCode.SETTLE_UP_PROFILE_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class SettleUpProfileNotFoundException : NotFoundException(SETTLE_UP_PROFILE_NOT_FOUND)
