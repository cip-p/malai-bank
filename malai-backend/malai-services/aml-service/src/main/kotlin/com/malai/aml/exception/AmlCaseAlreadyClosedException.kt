package com.malai.aml.exception

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.AML_CASE_ALREADY_CLOSED

class AmlCaseAlreadyClosedException :
    ConflictErrorException(AML_CASE_ALREADY_CLOSED),
    BusinessExceptionWhichShouldNotBeLogged
