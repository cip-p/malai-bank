package com.malai.accountholder.exception

import com.malai.commons.exception.MalaiCode.ACCOUNT_HOLDER_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class AccountHolderNotFoundException : NotFoundException(ACCOUNT_HOLDER_NOT_FOUND)
