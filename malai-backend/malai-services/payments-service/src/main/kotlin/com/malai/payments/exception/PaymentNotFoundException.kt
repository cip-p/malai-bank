package com.malai.payments.exception

import com.malai.commons.exception.MalaiCode.PAYMENT_NOT_FOUND
import com.malai.commons.exception.NotFoundException

class PaymentNotFoundException : NotFoundException(PAYMENT_NOT_FOUND)
