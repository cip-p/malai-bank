package com.malai.commons.tracking

object TestIdContext: ThreadLocalContext<TestId>("TestId", ::TestId)
