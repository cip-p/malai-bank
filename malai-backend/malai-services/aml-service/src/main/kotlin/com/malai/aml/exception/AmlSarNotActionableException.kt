package com.malai.aml.exception

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.AML_SAR_NOT_ACTIONABLE

class AmlSarNotActionableException :
    ConflictErrorException(AML_SAR_NOT_ACTIONABLE),
    BusinessExceptionWhichShouldNotBeLogged
