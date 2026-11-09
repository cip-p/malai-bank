package com.malai.payments.dedup

import org.springframework.data.jpa.repository.JpaRepository

interface SqsMessageDedupRepository : JpaRepository<SqsMessageDedupEntity, SqsMessageDedupId>
