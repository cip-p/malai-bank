package com.malai.accountholder.repository

import com.malai.accountholder.entity.AddressType
import com.malai.accountholder.entity.BusinessAddressEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface BusinessAddressRepository : JpaRepository<BusinessAddressEntity, UUID> {

    fun findByBusinessIdAndAddressType(businessId: UUID, addressType: AddressType): BusinessAddressEntity?
}
