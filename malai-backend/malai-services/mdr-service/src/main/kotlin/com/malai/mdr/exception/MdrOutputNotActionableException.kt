package com.malai.mdr.exception

import com.malai.commons.exception.BusinessExceptionWhichShouldNotBeLogged
import com.malai.commons.exception.ConflictErrorException
import com.malai.commons.exception.MalaiCode.MDR_OUTPUT_NOT_ACTIONABLE

// The gate refusing to serve an unruled Decision is its designed answer, not a failure:
// the payments resolver polls the internal actionable-output endpoint for every REFERRED
// payment until a ruling lands, and each pre-ruling poll is answered 409 by contract
// (MdrClient tolerates it as null). The marker keeps that steady-state protocol out of
// the ERROR log, exactly as InternalAccountNotFoundException does for account lookups.
class MdrOutputNotActionableException :
    ConflictErrorException(MDR_OUTPUT_NOT_ACTIONABLE),
    BusinessExceptionWhichShouldNotBeLogged
