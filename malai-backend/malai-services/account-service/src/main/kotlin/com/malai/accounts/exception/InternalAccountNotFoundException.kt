package com.malai.accounts.exception

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.MalaiCode.ACCOUNT_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class InternalAccountNotFoundException :
    NotFoundException(ACCOUNT_NOT_FOUND),
    BusinessExceptionWhichShouldNotBeLogged
