package com.malai.accounts.exception

import com.malai.commons.exception.MalaiCode.SPENDING_SPACE_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class SpendingSpaceNotFoundException : NotFoundException(SPENDING_SPACE_NOT_FOUND)
