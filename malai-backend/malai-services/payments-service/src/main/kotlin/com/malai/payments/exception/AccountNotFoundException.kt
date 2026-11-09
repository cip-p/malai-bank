package com.malai.payments.exception

import com.malai.commons.exception.MalaiCode.ACCOUNT_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class AccountNotFoundException : NotFoundException(ACCOUNT_NOT_FOUND)
