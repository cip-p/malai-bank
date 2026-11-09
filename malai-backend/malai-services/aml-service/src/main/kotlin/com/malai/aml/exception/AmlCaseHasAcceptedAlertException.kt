package com.malai.aml.exception

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.AML_CASE_HAS_ACCEPTED_ALERT

class AmlCaseHasAcceptedAlertException :
    ConflictErrorException(AML_CASE_HAS_ACCEPTED_ALERT),
    BusinessExceptionWhichShouldNotBeLogged
